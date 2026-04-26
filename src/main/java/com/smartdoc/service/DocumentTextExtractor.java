package com.smartdoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;

/**
 * Downloads a document from S3 and extracts raw text content.
 *
 * Why download from S3 and not use the original uploaded stream?
 * The original HTTP request stream is consumed during upload.
 * Consumer is a separate thread — by the time it processes
 * the message, the request is long gone. S3 is the source of truth.
 *
 * PDFBox is used for PDF text extraction.
 * For image files (PNG, JPG), a proper implementation would use
 * AWS Textract or Tesseract OCR. For now we return a placeholder
 * for non-PDF files — easily extensible in Module 6.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentTextExtractor {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Downloads file from S3 and extracts text content.
     *
     * @param s3Key      the S3 object key
     * @param contentType MIME type to determine extraction strategy
     * @return raw text content from the document
     */
    public String extractText(String s3Key, String contentType) {
        log.info("Downloading from S3 for text extraction: {}", s3Key);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object =
                     s3Client.getObject(getRequest)) {

            if ("application/pdf".equals(contentType)) {
                return extractPdfText(s3Object);
            } else if (contentType != null && contentType.startsWith("image/")) {
                // Images need OCR — placeholder for now
                // Module 6 enhancement: integrate AWS Textract
                log.info("Image file — returning placeholder text for now");
                return "Image document: " + s3Key + ". OCR not yet implemented.";
            } else {
                return "Unknown document type: " + contentType;
            }

        } catch (IOException e) {
            log.error("Failed to extract text from {}: {}", s3Key, e.getMessage());
            throw new RuntimeException("Text extraction failed", e);
        }
    }

    /**
     * Extracts text from a PDF using Apache PDFBox.
     * PDFTextStripper reads all pages and returns concatenated text.
     */
    private String extractPdfText(ResponseInputStream<GetObjectResponse> inputStream)
            throws IOException {

        // PDFBox 3.0 removed PDDocument.load() — must use Loader class now
        // RandomAccessReadBuffer reads the full stream into memory for random access
        byte[] bytes = inputStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF", text.length());
            return text;
        }
    }
}