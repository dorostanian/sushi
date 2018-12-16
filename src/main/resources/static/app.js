var editor;
var editorContainers;
var svg;
var render;
var g;

var awesomplete;
var actions;
var blocksIds;

var addedActionContainer = new Vue({
    el: '#added-action-container',
    data: {
        action: {
            type: "",
            name: "",
            docs: ""
        }
    }
});

var blockEditModal = new Vue({
    el: '#block-modal',
    data: {
        blockType: "",
        action: {
            id: "",
            type: "",
            name: "",
            docs: ""
        },
        branch: {
            id: "",
            on: ""
        }
    },

    methods: {
            joinNextBlocksString:
                function (blocks) {
                if (!blocks)
                    return '';
                else
                    return blocks.join(';');
                }
        }
});


var branchEditModal = new Vue({
    el: '#branch-modal',
    data: {
        branch: {
            id: "",
            on: ""
        }
    },

    methods: {
            joinNextBlocksString:
                function (blocks) {
                if (!blocks)
                    return '';
                else
                    return blocks.join(';');
                }
        }
});


var input = document.getElementById("actionsDir");
var nextBlocks = document.getElementById("nextBlocks");
awesomplete = new Awesomplete(input);
awesomplete_Ids = new Awesomplete(nextBlocks,
    {
        filter: function (text, input) {
            return Awesomplete.FILTER_CONTAINS(text, input.match(/[^,]*$/)[0]);
        },

        item: function (text, input) {
            return Awesomplete.ITEM(text, input.match(/[^,]*$/)[0]);
        },

        replace: function (text) {
            var before = this.input.value.match(/^.+,\s*|/)[0];
            this.input.value = before + text + "; ";
        }
    }
);


input.addEventListener("awesomplete-select", function (selection) {
    var action = actions.find(obj => obj.type == selection.text);
    addedActionContainer.action = action;
});

$(document).ready(function () {
    console.log("Starting up!");
    editor = ace.edit("editor");
    editorContainers = ace.edit("editor-containers");

    editor.setTheme("ace/theme/monokai");
    editor.getSession().setMode("ace/mode/javascript");
    editor.session.setMode("ace/mode/toml");

    editorContainers.setTheme("ace/theme/monokai");
    editorContainers.getSession().setMode("ace/mode/javascript");
    editorContainers.session.setMode("ace/mode/toml");

    setupDagre();

    populateActionsList();

});


function clearAction(val) {
    console.log("Value is " + val);
    var action = actions.find(obj => obj.type == val);
    if (action)
        addedActionContainer.action = action;
    else {
        addedActionContainer.action = {
            type: "",
            name: "",
            docs: ""
        };
    }
}

function populateActionsList() {
    $.ajax({
        type: "GET",
        url: "/getLibrary/",
        crossDomain: true,

    }).done(function (data) {
        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        actions = responseJson.library;
        appendInfo(logOutput);
        awesomplete.list = actions.map(x => x.type);
    });
}

function setupDagre() {


    svg = d3.select("svg");
    render = dagreD3.render();
    inner = d3.select("svg g"),
        zoom = d3.zoom().on("zoom", function () {
            inner.attr("transform", d3.event.transform);
        });
    zoom.transform(svg, d3.zoomIdentity);
    svg.call(zoom);

    svg.selectAll("rect").append("rect")
        .attr("y", 10)
        .attr("x", 10)
        .attr("height", 5)
        .attr("width", 5)
        .on("click", function (d, i) {
            console.log(" clicked!");
        })
        .on("contextmenu", function (d, i) {
            d3.event.preventDefault();
            // react on right-clicking
            console.log("Doubly clicked!");
        });
}


function drawGraph() {
    appendInfo("Requesting TOML to Digraph conversion!");
    setupDagre();
    $.ajax({
        type: "POST",
        url: "/tomlToDigraph",
        data: editor.getValue() + editorContainers.getValue(),
        crossDomain: true,

    }).done(function (data) {

        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var digraphData = responseJson.digraphData;
        blocksIds = responseJson.blocksIds;
        awesomplete_Ids.list = blocksIds;

        appendInfo(logOutput);

        drawGraphWithDigraphData(digraphData);


    }).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });

}


function drawGraphWithDigraphData(digraphData) {
    // Cleanup old graph
    var svg = d3.select("svg > g");
    svg.selectAll("*").remove();

    setupDagre();
    g = graphlibDot.read(digraphData);
    d3.select("svg g").call(render, g);
    // Set margins, if not present
    if (!g.graph().hasOwnProperty("marginx") &&
        !g.graph().hasOwnProperty("marginy")) {
        g.graph().marginx = 20;
        g.graph().marginy = 20;
    }

    g.graph().transition = function (selection) {
        return selection.transition().duration(500);
    };

    bindActions();

}

function appendInfo(text) {
    $("#shell-emulator").append("<li>" + text + "</li>");
    $('#shell-emulator').animate({scrollTop: $('#shell-emulator').prop("scrollHeight")}, 500);
}

function appendError(text) {
    $("#shell-emulator").append("<li style='color: red;'>" + text + "</li>");
    $('#shell-emulator').animate({scrollTop: $('#shell-emulator').prop("scrollHeight")}, 500);
}

function deleteAction(currentActionId) {
    $.ajax({
        type: "GET",
        url: "/deleteBlock/" + currentActionId,
        crossDomain: true
    }).done(
        function (data) {
            var responseJson = JSON.parse(data);
            var logOutput = responseJson.responseLog;
            var digraphData = responseJson.digraphData;
            var tomlText = responseJson.tomlData;
            blocksIds = responseJson.blocksIds;
            awesomplete_Ids.list = blocksIds;
            editor.setValue(tomlText, -1);
            appendInfo(logOutput);

            drawGraphWithDigraphData(digraphData);
        }
    );
}


function getAction(currentActionId) {
    $.ajax({
        type: "GET",
        url: "/getAction/" + currentActionId,
        crossDomain: true
    }).done(
        function (data) {
            var responseJson = JSON.parse(data);
            var logOutput = responseJson.responseLog;
            var block = responseJson.blockInfo;
            appendInfo(logOutput);

            if (block.mapping){
                branchEditModal.branch = block;
                $('#branch-modal').modal('show');
            }else{
                blockEditModal.action = block;
                $('#modal-action-none').prop('checked', true);
                $('#block-modal').modal('show');
            }

        }
    ).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });
}


function bindActions() {
    $("[id$='-remove']").click(
        function () {
            var strIdsplits = $(this).attr('id').split('-');
            var currentId = strIdsplits.slice(0, strIdsplits.length - 1).join('-');
            appendInfo("Request: Deleting the block " + currentId);
            deleteAction(currentId);
        }
    );
    $("[id$='-edit']").click(
        function () {
            // alert('Editing ' + $(this).attr('id'));
            var strIdsplits = $(this).attr('id').split('-');
            var currentId = strIdsplits.slice(0, strIdsplits.length - 1).join('-');
            appendInfo("Request: Editing the block: \"" + currentId + "\" , fetching info...");
            getAction(currentId);

        }
    );
}


function addActionToGraph(currentActionType) {
    appendInfo("Requesting TOML to Digraph conversion!");
    setupDagre();
    $.ajax({
        type: "POST",
        url: "/tomlToDigraph",
        data: editor.getValue() + '\n' + editorContainers.getValue(),
        crossDomain: true,

    }).done(function (data) {

        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var digraphData = responseJson.digraphData;

        appendInfo(logOutput);

        drawGraphWithDigraphData(digraphData);

        $.ajax({
            type: "GET",
            url: "/addAction/" + currentActionType,
            crossDomain: true
        }).done(function (data) {
                var responseJson = JSON.parse(data);
                var logOutput = responseJson.responseLog;
                var tomlData = responseJson.tomlData;
                var digraphData = responseJson.digraphData;
                blocksIds = responseJson.blocksIds;
                awesomplete_Ids.list = blocksIds;
                appendInfo(logOutput);
                editor.setValue(tomlData, -1);
                drawGraphWithDigraphData(digraphData);

            }
        ).fail(function (xhr, textStatus, errorThrown) {
            appendError(JSON.parse(xhr.responseText).responseLog);
        });

    }).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });
}


function updateAction() {
    var allParams = $('[id^=param_]');

    for (let param of allParams) {
        var strIdsplits = param.id.split('_');
        var currentParam = strIdsplits.slice(1, strIdsplits.length).join('_');

        blockEditModal.action.params[currentParam] = param.value;
    }

    blockEditModal.action.name = $('#modal-edit-action-name').val();
    blockEditModal.action.source = $('#modal-action-first').is(':checked');
    blockEditModal.action.returnAfterExec = $('#modal-action-return').is(':checked');


    var nextBlocksUpdated = $('#nextBlocks').val().split(';');
    blockEditModal.action.nextBlocks = [];
    for (let blockId of nextBlocksUpdated){
         blockEditModal.action.nextBlocks.push(blockId);
    }

    if ($('#modal-action-none').is(':checked')) {
        blockEditModal.action.source = false;
        blockEditModal.action.returnAfterExec = false;
    }


    console.log('Source = ' + blockEditModal.action.source);
    console.log('Return = ' + blockEditModal.action.returnAfterExec);


    appendInfo("Request: Edit Block!");
    $.ajax({
        type: "PUT",
        url: "/editBlock",
        data: JSON.stringify({
            id: blockEditModal.action.id,
            name: blockEditModal.action.name,
            params: blockEditModal.action.params,
            source: blockEditModal.action.source,
            returnAfterExec: blockEditModal.action.returnAfterExec,
            nextBlocks: blockEditModal.action.nextBlocks
        }),
        crossDomain: true,
        contentType: "application/json"

    }).done(function (data) {

        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var digraphData = responseJson.digraphData;
        var tomlData = responseJson.tomlData;
        blocksIds = responseJson.blocksIds;
        awesomplete_Ids.list = blocksIds;

        appendInfo(logOutput);
        editor.setValue(tomlData, -1);

        drawGraphWithDigraphData(digraphData);

        $('#block-modal').modal('hide');

    }).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });
}

function addBranch(){
    appendInfo("Requesting TOML to Digraph conversion!");
    setupDagre();
    $.ajax({
        type: "POST",
        url: "/tomlToDigraph",
        data: editor.getValue() + '\n' + editorContainers.getValue(),
        crossDomain: true,

    }).done(function (data) {

        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var digraphData = responseJson.digraphData;

        appendInfo(logOutput);

        drawGraphWithDigraphData(digraphData);

        $.ajax({
            type: "GET",
            url: "/addBranch",
            crossDomain: true
        }).done(function (data) {
                var responseJson = JSON.parse(data);
                var logOutput = responseJson.responseLog;
                var tomlData = responseJson.tomlData;
                var digraphData = responseJson.digraphData;
                appendInfo(logOutput);
                editor.setValue(tomlData, -1);
                drawGraphWithDigraphData(digraphData);

            }
        ).fail(function (xhr, textStatus, errorThrown) {
            appendError(JSON.parse(xhr.responseText).responseLog);
        });

    }).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });
}


function signOut(){
    $.ajax({
        type: "GET",
        url: "/signOut",
        crossDomain: true,
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        }

    }).done(function (data) {
        console.log(data);
        if (data == 'OK') {
            console.log("redirecting...");
            location.reload();
        }

    }).fail(function (xhr, textStatus, errorThrown) {
    });

}



function addMoreParam(){
   console.log("Adding more param");
   blockEditModal.action.params["new-param"] = 'new-value';
}

function executeFlow() {
    appendInfo("Request: executing the flow...")
    $.ajax({
        type: "POST",
        url: "/executeFlow",
        data: editor.getValue() + '\n' + editorContainers.getValue(),
        crossDomain: true,
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        }

    }).done(function (data) {

        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var digraphData = responseJson.digraphData;

        appendInfo(logOutput);
        drawGraphWithDigraphData(digraphData);

    }).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });
}

var ws = new WebSocket('ws://localhost:8080/ws');
ws.onopen = function () {
    appendInfo('Connected to backend!');
};
ws.onclose = function () {
    appendError('Disconnected from backend!');
};

ws.onmessage = function (event) {
    var data = JSON.parse(event.data);
    if (data.first == 'ERROR')
        appendError(data.second)
    else
        appendInfo(data.second);
};

