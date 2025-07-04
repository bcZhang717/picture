package com.zbc.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.zbc.annotations.AuthCheck;
import com.zbc.constants.UserConstant;
import com.zbc.domain.vo.BaseResponse;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.manage.CosManage;
import com.zbc.utils.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * 测试通用文件上传和下载的接口
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {
    @Resource
    private CosManage cosManage;

    /**
     * 测试文件上传接口(CosManage)
     *
     * @param multipartFile 要上传的文件
     * @return 文件路径
     */
    @PostMapping("/test/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 获取原始文件名
        String filename = multipartFile.getOriginalFilename();
        // 拼接文件路径(构造文件夹)
        String filePath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 创建临时文件, 文件前缀是filePath,后缀默认是.tmp
            file = File.createTempFile(filePath, null);
            // 将上传的文件保存到服务器指定的位置,要求参数默认不存在才创建
            multipartFile.transferTo(file);
            // 上传文件到COS
            cosManage.putObject(filePath, file);
            return ResultUtils.success(filePath);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean deleted = file.delete();
                if (!deleted) {
                    log.error("文件删除失败: {}", filePath);
                }
            }
        }
    }

    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManage.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            // 处理下载到的流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }
}
