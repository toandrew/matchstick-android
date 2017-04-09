
var timerID = -1;

function checkFileType(path)
{
	var type = path.substring(path.lastIndexOf(".")+1);
	if(type=="apk")
	{
		return true;
	}
	
	return false;
}

function startUpload(index)
{
	var path = "";
	var curUrl;
	var fileName;
	var formID;
	
	if(index==0)
	{
		path = $("#file_uploader2").val();
		formID = "#form2";
	}
	else
	{
		path = $("#file_uploader").val();
		formID = "#form";
	}
	
	if(path.length==0)
	{
		alert("文件未选择");
		return;
	}
	
	if(!checkFileType(path))
	{
		alert("只支持apk文件格式！");
		return;
	}
	
	fileName = path.substring(path.lastIndexOf("\\")+1);

	$("#fileName").text(fileName);
	$("#resultInfo").text("正在发送");
	
	$(formID).ajaxSubmit({
        success: function (responseText) {
            //这里请对应修改上传文件返回值，不要轻易把我删掉
        	clearTimeout(timerID);
        	timerID = -1;

        	alert("上传成功！");
        	
        	location.reload();
        }
    });

    if(timerID!=-1)
    {
    	clearTimeout(timerID);
    }

    timerID = setTimeout("alert(\"上传失败\");location.reload();",90000);
	
}

$(document).ready(function () {
	$("#links_input").focus(function(){
		if($("#links_input").val()=="请输入APK链接地址")
		{
			$("#links_input").val("");
		}
	});
	
	$("#links_input").focusout(function(){
		if($("#links_input").val()=="")
		{
			$("#links_input").val("请输入APK链接地址");
		}
	});
	
	
	function startUploadApk()
	{
		var inputUrl = $("#links_input").val();
		var requestUrl = "/apkurl";

		if(inputUrl=="")
		{
			alert("输入不能为空！");
			return;
		}
		
		if(inputUrl=="请输入APK链接地址")
		{
			alert("请输入APK链接地址！");
			return;
		}
		
		$.get(requestUrl,{url:encodeURI(inputUrl)},function(data){
			clearTimeout(timerID);
        	timerID = -1;
			var result = parseInt(data);
        	switch(result)
        	{
        		case 1:
        			alert("安装成功!");
        			break;
        		case 0:
        			alert("下载成功，安装失败!");
        			break;
        		case 2:
        			alert("链接已发送，请等待安装...");
        			break;
        		default:
        			alert("下载失败！");
        			break;
        	}
			
		});
		
		if(timerID!=-1)
    	{
    		clearTimeout(timerID);
   		}

    	timerID = setTimeout("alert(\"上传失败\");",90000);
		
	}
	
	$("#links_input").keydown(function(event){
		if(event.keyCode==13)
		{
			startUploadApk();
		}
	});
	
	$("#remote_upload").bind("click",function(){
		startUploadApk();
	});
	
});


