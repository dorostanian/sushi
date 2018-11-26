var editor;
var svg;
var render;
var g;

var awesomplete;
var actions;

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
        action: {
            id: "",
            type: "",
            name: "",
            docs: ""
        }
    }
});


var input = document.getElementById("actionsDir");
awesomplete = new Awesomplete(input);

input.addEventListener("awesomplete-select", function (selection) {
    var action = actions.find(obj => obj.type == selection.text);
    addedActionContainer.action = action;
});

$(document).ready(function () {
    console.log("Starting up!");
    editor = ace.edit("editor");
    editor.setTheme("ace/theme/monokai");
    editor.getSession().setMode("ace/mode/javascript");
    editor.session.setMode("ace/mode/toml");

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
        url: "http://localhost:8080/getLibrary/",
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
}


function drawGraph() {
    appendInfo("Requesting TOML to Digraph conversion!");
    setupDagre();
    $.ajax({
        type: "POST",
        url: "/tomlToDigraph",
        data: editor.getValue(),
        crossDomain: true,

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
        url: "/deleteBlock" + currentActionId,
        crossDomain: true
    }).done(
        function (data) {
            var responseJson = JSON.parse(data);
            var logOutput = responseJson.responseLog;
            var digraphData = responseJson.digraphData;
            var tomlText = responseJson.tomlData;
            // console.log(tomlText);
            editor.setValue(tomlText, -1);
            appendInfo(logOutput);

            drawGraphWithDigraphData(digraphData);
        }
    );
}


function getAction(currentActionId) {
    $.ajax({
        type: "GET",
        url: "http://localhost:8080/getAction/" + currentActionId,
        crossDomain: true
    }).done(
        function (data) {
            var responseJson = JSON.parse(data);
            var logOutput = responseJson.responseLog;
            var action = responseJson.blockInfo;
            appendInfo(logOutput);

            blockEditModal.action = action;

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
            appendInfo("Requesting to delete block " + currentId);
            deleteAction(currentId);
        }
    );
    $("[id$='-edit']").click(
        function () {
            // alert('Editing ' + $(this).attr('id'));
            var strIdsplits = $(this).attr('id').split('-');
            var currentId = strIdsplits.slice(0, strIdsplits.length - 1).join('-');
            appendInfo("Editing block: \"" + currentId + "\" , fetching info...");
            getAction(currentId);
            $('#block-modal').modal('show');
        }
    );
}


function addActionToGraph(currentActionType) {

    appendInfo("Requesting TOML to Digraph conversion!");
    setupDagre();
    $.ajax({
        type: "POST",
        url: "/tomlToDigraph",
        data: editor.getValue(),
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

