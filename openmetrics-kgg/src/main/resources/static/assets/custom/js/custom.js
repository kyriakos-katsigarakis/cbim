$(document).ready(function () {

	$("#btn-download").click(function () {
		location.href = '/download';
	});

	$("#file").click(function() {
		$("#btn-download").css('display','none');
		$("#file").removeClass("is-invalid");
	});

    $("#file").on('change', function(event) {
		event.preventDefault();
		$("#btn-download").css('display','none');
        var form = $('#form-upload')[0];
        var data = new FormData(form);
        $.ajax({
            type: "POST",
            enctype: "multipart/form-data",
            url: "/upload",
            data: data,
            processData: false,
            contentType: false,
            cache: false,
            timeout: 600000,
            beforeSend: function(data) {
				$("#loading").css('display','block');
			},
            success: function (data) {
				$("#loading").css('display','none');
                console.log(data);
               	if(data == "success"){
	 				$("#btn-download").css('display','block');
				}else if(data == "failed_extension"){
					 $("#file").addClass("is-invalid");					
				}else if(data == "failed_schema"){
					 $("#file").addClass("is-invalid");
				}
                $("#file").val(null);
            },
            error: function (e) {
                $("#loading").css('display','none');
                $("#file").addClass("is-invalid");
            }
        });
	});
});