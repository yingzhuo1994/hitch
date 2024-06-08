package com.heima.account.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heima.commons.enums.BusinessErrors;
import com.heima.commons.exception.BusinessRuntimeException;
import com.heima.modules.po.VehiclePO;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@Component
public class AiHelper {
    @Value("${baidu.apikey}")
    private String API_KEY;
    @Value("${baidu.secretkey}")
    private String SECRET_KEY;

    private final static Logger logger = LoggerFactory.getLogger(AiHelper.class);

    final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder().build();


    public static void main(String []args) throws IOException {
        String code = new AiHelper().getLicense(null);
        System.out.println(code);
    }

    /*

    图像识别，获取车牌信息
    文档（行驶证识别）：https://cloud.baidu.com/doc/OCR/s/yk3h7y3ks
    文档（车牌识别）：https://cloud.baidu.com/doc/OCR/s/ck3h7y191
    获取车辆照片url
    将url下载到某个临时文件夹
    将文件编码为base64
    调百度AI接口，返回对应信息
    对比：行驶证车牌 和 车辆车牌是否一致
    如果一致，设置车牌信息，认证通过，身份变更为车主

    简化版业务流程（至少完成）：识别车辆车牌号即可

    * */
    public String getLicense(VehiclePO vehiclePO) throws IOException {
        // Check if vehiclePO or carFrontPhoto is null
        if (vehiclePO == null || vehiclePO.getCarFrontPhoto() == null) {
            throw new BusinessRuntimeException(BusinessErrors.DATA_NOT_EXIST, "未上传前车照片");
        }

        String carFrontPhotoUrl = vehiclePO.getCarFrontPhoto();

        // Step 1: Download the image from the URL to a temporary file
        File tempFile = downloadImage(carFrontPhotoUrl);

        // Step 2: Encode the image file to Base64
        String base64Image = encodeFileToBase64(tempFile);

        // Step 3: Get access token from Baidu AI
        String accessToken = getAccessToken(API_KEY, SECRET_KEY);

        // Step 4: Call Baidu AI API for license plate recognition
        String licensePlateNumber = recognizeLicensePlate(base64Image, accessToken);

        // Clean up the temporary file
        if (tempFile.exists()) {
            tempFile.delete();
        }

        return licensePlateNumber;
    }

    private File downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        File tempFile = Files.createTempFile("car_front_photo", ".jpg").toFile();
        FileUtils.copyURLToFile(url, tempFile);
        return tempFile;
    }

    private String encodeFileToBase64(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileContent);
    }

    private String getAccessToken(String apiKey, String secretKey) throws IOException {
        String authUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;

        Request request = new Request.Builder().url(authUrl).build();
        Response response = HTTP_CLIENT.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        String responseBody = response.body().string();
        JSONObject jsonObject = new JSONObject(responseBody);
        return jsonObject.getString("access_token");
    }

    private String recognizeLicensePlate(String base64Image, String accessToken) throws IOException {
        String ocrUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/license_plate";

        RequestBody requestBody = new FormBody.Builder()
                .add("image", base64Image)
                .build();

        Request request = new Request.Builder()
                .url(ocrUrl + "?access_token=" + accessToken)
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        Response response = HTTP_CLIENT.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        String responseBody = response.body().string();
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject wordsResult = jsonObject.getJSONObject("words_result");
        return wordsResult.getString("number");
    }
}
