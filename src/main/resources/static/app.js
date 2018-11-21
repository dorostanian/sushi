var editor;
var svg;
var render;
var g;


$(document).ready(function () {
    console.log("Starting up!");
    editor = ace.edit("editor");
    editor.setTheme("ace/theme/monokai");
    editor.getSession().setMode("ace/mode/javascript");
    editor.session.setMode("ace/mode/toml");

    setupDagre();

    populateActionsList();

});

function populateActionsList() {
    $.ajax({
        type: "GET",
        url: "http://localhost:8080/getLibrary/",
        crossDomain: true,

    }).done(function (data) {
        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var action, actions = responseJson.library;

        appendInfo(logOutput);

        $("#actions-list div").remove();
        for (action of actions) {
            addActionToList(action);
        }

    });
}

function setupDagre() {
    svg = d3.select("svg");
    render = dagreD3.render();
    inner = d3.select("svg g"),
        zoom = d3.zoom().on("zoom", function () {
            inner.attr("transform", d3.event.transform);
        });
    svg.call(zoom);
}


function drawGraph() {
    appendInfo("Requesting TOML to Digraph conversion!");
    setupDagre();
    $.ajax({
        type: "POST",
        url: "http://localhost:8080/tomlToDigraph",
        data: editor.getValue(),
        crossDomain: true,

    }).done(function (data) {

        var responseJson = JSON.parse(data);
        var logOutput = responseJson.responseLog;
        var digraphData = responseJson.digraphData;

        appendInfo(logOutput);
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

    }).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });

}


function appendInfo(text) {
    $("#shell-emulator").append("<li>" + text + "</li>");
}

function appendError(text) {
    $("#shell-emulator").append("<li style='color: red;'>" + text + "</li>");
}

function deleteAction(currentActionId) {
    $.ajax({
        type: "GET",
        url: "http://localhost:8080/deleteAction/" + currentActionId,
        crossDomain: true
    }).done(
        function (data) {
            var responseJson = JSON.parse(data);
            var logOutput = responseJson.responseLog;
            var digraphData = responseJson.digraphData;
            var tomlText = responseJson.tomlData;
            console.log(tomlText);
            editor.setValue(tomlText, -1);
            appendInfo(logOutput);


            setupDagre();
            g = graphlibDot.read(digraphData);
            // Render the graph into svg g
            d3.select("svg g").call(render, g);
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

            $("#modal-action-id").html("ID: " + action.id);
            $("#modal-action-name").val(action.name);
            $("#modal-action-type").html("Type: " + action.type);
            $("#modal-action-description").html(action.description);

            var params = action.params;
            $("#modal-action-params tr").remove();
            for (var k in params) {
                var paramRow = "<tr><td>" + k + "</td>" +
                    "<td><input class='form-control form-control-sm' type='text' value='" + params[k] + "'></td>" +
                    //  "<td><button class='badge badge-danger badge-pill'>remove</button>" +
                    "</tr>";
                $("#modal-action-params").append(paramRow);
            }
            // $("#modal-action-id").html(currentActionId);

        }
    ).fail(function (xhr, textStatus, errorThrown) {
        appendError(JSON.parse(xhr.responseText).responseLog);
    });
}

function addMoreParam() {

}

function bindActions() {
    $("[id$='-remove']").click(
        function () {
            var strIdsplits = $(this).attr('id').split('-');
            var currentId = strIdsplits.slice(0, strIdsplits.length - 1).join('-');
            appendInfo("Requesting to delete action " + currentId);
            deleteAction(currentId);
        }
    );
    $("[id$='-edit']").click(
        function () {
            // alert('Editing ' + $(this).attr('id'));
            var strIdsplits = $(this).attr('id').split('-');
            var currentId = strIdsplits.slice(0, strIdsplits.length - 1).join('-');
            appendInfo("Editing block: \"" + currentId + "\" , fetching info...");
            $('#action-modal').modal('show');
            getAction(currentId);
        }
    );
}


function addActionToList(action) {
    var toAppend = "<div class=\"list-group-item list-group-item-action flex-column align-items-start active\">\n" +
        " <div class=\"d-flex w-100 justify-content-between\">\n" +
        "                            <h5 class=\"mb-1\">" + action.type + "</h5>\n" +
        "                        </div>\n" +
        "                        <p class=\"mb-1\">" + action.description + "</p>\n" +
        "                        <br>\n" +
        "           <button class=\"badge badge-info badge-pill\" id='" + action.type + "' >add this action</button>\n" +
        "</div>"

    $("#actions-list").append(toAppend);
}
