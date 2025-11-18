package com.example.demo.controller;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.util.Iterator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/images")
public class ImageProxyController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    /**
     * Proxy and lightly protect vehicle images. Serves a resized, watermarked JPEG derivative.
     * Example: /api/images/vehicles/79/abc.jpg -> fetches from Supabase public bucket
     */
    @GetMapping("/vehicles/{registrationId}/{filename:.+}")
    public ResponseEntity<byte[]> proxyVehicleImage(
            @PathVariable("registrationId") String registrationId,
            @PathVariable("filename") String filename,
            @RequestParam(value = "w", required = false) Integer maxW,
            @RequestParam(value = "q", required = false) Float quality,
            @RequestParam(value = "mark", required = false, defaultValue = "1") int addMark
    ) {
        try {
            // Build public source URL in storage
            String source = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + registrationId + "/" + filename;

            try (InputStream in = new URL(source).openStream()) {
                BufferedImage src = ImageIO.read(in);
                if (src == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

                // Normalize to RGB (no alpha) for JPEG
                int type = BufferedImage.TYPE_INT_RGB;
                int w = src.getWidth();
                int h = src.getHeight();

                // Resize if needed
                int targetW = w;
                int targetH = h;
                int limit = (maxW != null && maxW > 200) ? maxW : 1280; // default cap
                if (w > limit) {
                    targetW = limit;
                    targetH = (int) Math.round((limit / (double) w) * h);
                }

                BufferedImage canvas = new BufferedImage(targetW, targetH, type);
                Graphics2D g = canvas.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(src, 0, 0, targetW, targetH, null);

                // Optional watermark: diagonal, semi-transparent "HPG"
                if (addMark == 1) {
                    String mark = "HPG";
                    float alpha = 0.12f; // subtle but visible
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g.setColor(new Color(255, 255, 255));
                    int fontSize = Math.max(24, (int) (targetW * 0.12));
                    g.setFont(new Font("Arial", Font.BOLD, fontSize));

                    // Rotate and tile the watermark across the image
                    AffineTransform orig = g.getTransform();
                    g.rotate(Math.toRadians(-30), targetW / 2.0, targetH / 2.0);
                    int step = (int) (fontSize * 3.0);
                    for (int y = -targetH; y < targetH * 2; y += step) {
                        for (int x = -targetW; x < targetW * 2; x += step) {
                            g.drawString(mark, x, y);
                        }
                    }
                    g.setTransform(orig);
                }
                g.dispose();

                // Encode JPEG with configurable quality
                float q = (quality != null && quality > 0 && quality <= 1) ? quality : 0.82f;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                if (!writers.hasNext()) {
                    // Fallback to PNG if JPEG writer missing
                    ImageIO.write(canvas, "png", baos);
                    return buildImageResponse(baos.toByteArray(), MediaType.IMAGE_PNG_VALUE);
                }
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(q);
                }
                try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(canvas, null, null), param);
                } finally {
                    writer.dispose();
                }

                return buildImageResponse(baos.toByteArray(), MediaType.IMAGE_JPEG_VALUE);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private ResponseEntity<byte[]> buildImageResponse(byte[] bytes, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().getHeaderValue());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
