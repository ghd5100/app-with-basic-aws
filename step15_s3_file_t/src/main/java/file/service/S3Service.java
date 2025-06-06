package file.service;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.internal.S3AbortableInputStream;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class S3Service {
	
	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;
	
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;
    
    @Value("${file.upload-dir}")
    private String savePath;
    
    private final String DIR_NAME = "s3_data";

    
    @Transactional
    public void uploadS3File(MultipartFile file) throws Exception {
        if(file == null) {
            throw new Exception("파일 전달 오류 발생");
        }

        String attachmentOriginalFileName = file.getOriginalFilename();
        UUID uuid = UUID.randomUUID();
        String attachmentFileName = uuid.toString()+"_"+attachmentOriginalFileName;
        Long attachmentFileSize = file.getSize();

        AttachmentFile attachmentFile = AttachmentFile.builder()
            .attachmentFileName(attachmentFileName)
            .attachmentOriginalFileName(attachmentOriginalFileName)
            .filePath(savePath)
            .attachmentFileSize(attachmentFileSize)
            .build();

        Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();

        if(fileNo != null) {
            File uploadFile = tempFileSave(file, attachmentFileName, attachmentFile);
            transferFileS3(uploadFile);
            if(uploadFile.exists()) {
                uploadFile.delete();
            }
        }
    }

	private void transferFileS3(File uploadFile) {
		//S3 파일 전송
		//bucker: 버킷
		//key : 객체의 저장 경로 + 객체의 이름
		//file: 물리적 리소스
		String key = DIR_NAME + "/" + uploadFile.getName();
		
		amazonS3.putObject(bucketName, key, uploadFile);
	}

	
    private File tempFileSave(MultipartFile file, String attachmentFileName, AttachmentFile attachmentFile)
            throws IOException {
        File uploadFile = new File(savePath + "/" + attachmentFileName);
        file.transferTo(uploadFile);
        return uploadFile;
    }
	
	
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		AttachmentFile attachmentFile = null;
		Resource resource = null;
		
		// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
		attachmentFile = fileRepository.getReferenceById(fileNo);
		fileRepository.findById(fileNo).orElseThrow(()-> new NoSuchElementException("파일없음"));
		
		String key = DIR_NAME + "/" + attachmentFile.getAttachmentFileName();

		//객체 가져오기
		S3Object s3Object = amazonS3.getObject(bucketName, key);
		S3ObjectInputStream s3is = s3Object.getObjectContent();
		resource = new InputStreamResource(s3is);	
	
		HttpHeaders headers= new HttpHeaders();
		
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition(ContentDisposition.builder("attachment")
				.filename(attachmentFile.getAttachmentOriginalFileName())
				.build());
		
		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
	}
	
}