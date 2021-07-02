$(document).ready(function () {

   	$("#btn-download").hide();

	$("#btn-download").click(function () {
		location.href = '/download';
	});

    $("#file").on('change', function(event) {
		event.preventDefault();
        var form = $('#form-upload')[0];
        var data = new FormData(form);
        $("#btn-form-upload").prop("disabled", true);
        $.ajax({
            type: "POST",
            enctype: "multipart/form-data",
            url: "/upload",
            data: data,
            processData: false,
            contentType: false,
            cache: false,
            timeout: 600000,
            success: function (data) {
                console.log(data);
               	if(data == "success"){
	 				$("#btn-download").show();
				}else if(data == "failed_extension"){
					
				}else if(data == "failed_schema"){
					
				}
                $("#file").val(null);
            },
            error: function (e) {
                console.log(e);
            }
        });
	});
});