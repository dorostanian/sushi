


// function graphToURL() {
//     var elems = [window.location.protocol, '//',
//                  window.location.host,
//                  window.location.pathname,
//                  '?'];
  
//     var queryParams = [];
//     if (debugAlignment) {
//       queryParams.push('alignment=' + debugAlignment);
//     }
//     queryParams.push('graph=' + encodeURIComponent(inputGraph.value));
//     elems.push(queryParams.join('&'));
  
//     return elems.join('');
//   }
  

  function tryDraw() {
    console.log("Came here!");
    if (oldInputGraphValue !== inputGraph.value) {
      inputGraph.setAttribute("class", "");
      oldInputGraphValue = inputGraph.value;
      try {
        g = graphlibDot.read(inputGraph.value);
      } catch (e) {
        inputGraph.setAttribute("class", "error");
        throw e;
      }
  
      // Save link to new graph
    //   graphLink.attr("href", graphToURL());
  
      // Set margins, if not present
      if (!g.graph().hasOwnProperty("marginx") &&
          !g.graph().hasOwnProperty("marginy")) {
        g.graph().marginx = 20;
        g.graph().marginy = 20;
      }
  
      g.graph().transition = function(selection) {
        return selection.transition().duration(500);
      };
  
      // Render the graph into svg g
      d3.select("svg g").call(render, g);
    }
  }