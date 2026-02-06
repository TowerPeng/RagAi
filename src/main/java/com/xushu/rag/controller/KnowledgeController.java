package com.xushu.rag.controller;

/**
 * @Title: KnowledgeController
 * @Author Xushu
 * @Package com.Xushu.rag.controller
 * @Date 2025/2/8 20:35
 * @description: 知识库
 */

import com.alibaba.fastjson2.JSON;
import com.xushu.rag.common.ApplicationConstant;
import com.xushu.rag.common.BaseResponse;
import com.xushu.rag.common.ErrorCode;
import com.xushu.rag.common.ResultUtils;
import com.xushu.rag.entity.AliOssFile;
import com.xushu.rag.pojo.dto.QueryFileDTO;
import com.xushu.rag.service.AliOssFileService;
import com.xushu.rag.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "KnowledgeController", description = "知识库管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge")
public class KnowledgeController {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private TokenTextSplitter tokenTextSplitter;



    @Autowired
    private AliOssFileService aliOssFileService;
    /**
     * 上传附件接口
     *
     *  1. 提供不同的分片策略
     *  2. 分片后的预览
     * @param
     * @return
     * @throws IOException
     */

    @Operation(summary = "upload", description = "上传附件接口")
    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    public BaseResponse upload(@RequestParam("file") List<MultipartFile> files) {
        //上传文件
        for (MultipartFile file : files) {
            try {
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                String objectName = UUID.randomUUID().toString() + extension;
                String url = aliOssUtil.upload(file.getBytes(), objectName);

                //向量化
                //1、读取文件 txt pdf docx doc
                Resource resource = file.getResource();
                TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
                List<Document> documents = tikaDocumentReader.read();
                //2、分词
                List<Document> apply = tokenTextSplitter.apply(documents);
                //3、向量化
                //4、保存向量
                vectorStore.add( apply);
                long currentTimeMillis = System.currentTimeMillis();
                //持久化到数据库
                aliOssFileService.save(AliOssFile.builder()
                        .fileName(originalFilename)
                        .vectorId(JSON.toJSONString(documents.stream().map(Document::getId).collect(Collectors.toList())))
                        .url(url)
                        .createTime(new Date(currentTimeMillis))
                        .updateTime(new Date(currentTimeMillis))
                        .build());
            } catch (IOException e) {
                log.error("文件上传失败",e);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"文件上传失败");
            }catch (Exception e){
                log.error("文件上传失败",e);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"文件上传失败");
            }
        }
        return ResultUtils.success("文件上传成功");
    }


    @Operation(summary = "contents",description = "文件查询")
    @GetMapping("/contents")
    public BaseResponse queryFiles(QueryFileDTO request){
        if(request.getPage() == null || request.getPageSize() == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"page 或 pageSize为空");
        }
        return aliOssFileService.queryPage(request);
    }

    @Operation(summary = "delete",description = "文件删除")
    @DeleteMapping("/delete")
    public BaseResponse deleteFiles(@RequestParam List<Long> ids){
        return aliOssFileService.deleteFiles(ids);
    }


    @Operation(summary = "download",description = "文件下载")
    @GetMapping("/download")
    public BaseResponse downloadFiles(@RequestParam List<Long> ids){
        return aliOssFileService.downloadFiles(ids);
    }





}
