$('#debug-button').on('click', issueQuery);

$('#query-input').keypress(function (event) {
  if (event.which == 13) {
    issueQuery()
  }
});

$('.activate-tooltip').tooltip()

function setupPopoverContent(buttonTd, wid, features){
  buttonTd.append($( "<button>" ).attr("class", "btn btn-default glyphicon glyphicon-plus").attr("data-container", "body").attr("data-toggle", "popover").attr("type", "button"));
  table = $( "<table>" ).attr("class", "table");
  $( "<thead><tr><th>Feature name</th><th>Value</th></tr></thead>" ).appendTo(table);
  tbody = $( "<tbody>" );
  tbody.appendTo(table);
  $.each(features, function(key, value){
    $("<tr>").append($("<td>").html(key)).append($("<td>").html(value)).appendTo(tbody);
  });
  pop = buttonTd.popover({trigger: "focus", content: table, placement: "left", html: true, container:"body"})
}

function showLoading(){
  buttonText = $('#debug-button').text()
  $('#debug-button').html("Loading...")
  $("#cat-loading").attr("src", "running-cat.gif")
}

function stopLoading(){
  $('#debug-button').html(buttonText)
  $("#cat-loading").attr("src", "sitting-cat.png")
}

function issueQuery() {
  query = $('#query-input').val()
  showLoading();
  fillIn(query);
}

function fillIn(query) {
    var debugAPI = "rest/debug";
    $.getJSON( debugAPI, {Text: query,})
    .done(
      function( data ) {
	stopLoading();
	$.each(data,
	       function (query, qdata){
		 $( "#query" ).html( query );
		 $( ".clear-data" ).empty()
		 $.each(qdata.phase1.source6.annotatedSnippets,
			function (rank){
			  var tr = $( "<tr>" );
			  tr.appendTo($("#annotatedSnippetsTable"));
			  $( "<td>" ).html(rank).appendTo(tr);
			  var snippetTd = $( "<td>" ).appendTo(tr);
			  $.each(this.parts,
					  function (){
				  var textnode = $( "<span>" ).appendTo(snippetTd);
				  if (this.url)
					  textnode = $( "<a>" ).attr("href", this.url).attr("target", "_blank").appendTo(textnode);
				  
				  var lastIdx = 0;
				  var text = this.text;
				  $.each(this.bolds,
						  function (){
					  $( "<span>" ).html(text.substring(lastIdx, this.begin)).appendTo(textnode);
					  $( "<b>" ).html(text.substring(this.begin, this.end)).appendTo(textnode);
					  lastIdx = this.end;
				  });
				  $( "<span>" ).html(text.substring(lastIdx)).appendTo(textnode);
			  }
			  );
		 }
		 );
		$.each(qdata.phase1.source6.entityFeatures,
			function (){
			  var tr = $( "<tr>" );
			  tr.appendTo("#entitiesSource6Table");
			  $( "<td>" ).html(this.wid).appendTo(tr);
			  var linkTd = $( "<td>" );
			  linkTd.appendTo(tr);
			  $( "<a>" ).attr("href", this.url).attr("target", "_blank").html(this.title).appendTo(linkTd);
			  var buttonTd = $( "<td>" );
			  buttonTd.appendTo(tr);
			  var acceptedTd = $( "<td>" );
			  acceptedTd.appendTo(tr);
			  if (this.accepted)
			    $("<span>").attr("class", "glyphicon glyphicon-ok green").appendTo(acceptedTd);
			  else
			    $("<span>").attr("class", "glyphicon glyphicon-remove red").appendTo(acceptedTd);
			  
			  // setup popover for feature button
			  setupPopoverContent(buttonTd, this.wid, this.features);
			});
		 var widToFtrsS2 = {};
		 var widToAcceptedS2 = {};
		 $.each(qdata.phase1.source2.entityFeatures,
			function (){
			  widToFtrsS2[this.wid] = this.features;
			  widToAcceptedS2[this.wid] = this.accepted;
			});
		
		 $.each(qdata.phase1.source2.pages,
			function (){
			  var tr = $( "<tr>" );
			  tr.appendTo("#pagesSource2Table");
			  $( "<td>" ).html(this.rank).appendTo(tr);
			  var urlTd = $( "<td>" ).appendTo(tr);
			  urlTd.append($("<a>").attr("href",this.url).attr("target", "_blank").html((this.url.length < 45) ? this.url : this.url.substring(0,45)+"..."));
			  $( "<td>" ).html(this.wid).appendTo(tr);
			  var linkTd = $( "<td>" );
			  linkTd.appendTo(tr);
			  $( "<a>" ).attr("href", this.url).attr("target", "_blank").html(this.title).appendTo(linkTd);
			  var buttonTd = $( "<td>" );
			  buttonTd.appendTo(tr);
			  var acceptedTd = $( "<td>" );
			  acceptedTd.appendTo(tr);
			  if (this.wid in widToAcceptedS2)
			    if (widToAcceptedS2[this.wid])
			      $("<span>").attr("class", "glyphicon glyphicon-ok green").appendTo(acceptedTd);
			    else
			      $("<span>").attr("class", "glyphicon glyphicon-remove red").appendTo(acceptedTd);
			  
			  // setup popover for feature button
			  if (this.wid in widToFtrsS2)
			    setupPopoverContent(buttonTd, this.wid, widToFtrsS2[this.wid]);
			});

		 var widToFtrsS3 = {};
		 var widToAcceptedS3 = {};
		 $.each(qdata.phase1.source3.entityFeatures,
			function (){
			  widToFtrsS3[this.wid] = this.features;
			  widToAcceptedS3[this.wid] = this.accepted;
			});
		
		 $.each(qdata.phase1.source3.pages,
			function (){
			  var tr = $( "<tr>" );
			  tr.appendTo("#pagesSource3Table");
			  $( "<td>" ).html(this.rank).appendTo(tr);
			  var urlTd = $( "<td>" ).appendTo(tr);
			  urlTd.append($("<a>").attr("href",this.url).attr("target", "_blank").html((this.url.length < 45) ? this.url : this.url.substring(0,45)+"..."));
			  $( "<td>" ).html(this.wid).appendTo(tr);
			  linkTd = $( "<td>" );
			  linkTd.appendTo(tr);
			  $( "<a>" ).attr("href", this.url).attr("target", "_blank").html(this.title).appendTo(linkTd);
			  var buttonTd = $( "<td>" );
			  buttonTd.appendTo(tr);
			  var acceptedTd = $( "<td>" );
			  acceptedTd.appendTo(tr);
			  if (this.wid in widToAcceptedS3)
			    if (widToAcceptedS3[this.wid])
			      $("<span>").attr("class", "glyphicon glyphicon-ok green").appendTo(acceptedTd);
			    else
			      $("<span>").attr("class", "glyphicon glyphicon-remove red").appendTo(acceptedTd);
			  
			  // setup popover for feature button
			  if (this.wid in widToFtrsS3)
			    setupPopoverContent(buttonTd, this.wid, widToFtrsS3[this.wid]);
			});
		 var span = $("<span>").append($("<strong>Found: </strong> "))
		 $.each(qdata.results,
			function (idx){
			  span.append(query.substring(this.begin, this.end) + " &rarr; ");
			  span.append($("<a>").attr("href", this.url).attr("target", "_blank").html(this.title));
			  if (idx != qdata.results.length -1)
				  span.append(" &dash; ");
			});
		 if (qdata.results.length == 0)
		   $("#result-alert").html($("<strong>").html("No entities found for query: "+query))
		 else
		   $("#result-alert").html(span)
	       })
	
      })
    .fail(function( jqxhr, textStatus, error ) {
      var err = textStatus + ", " + error;
      alert( "Request Failed: " + err );
    }
     );
}