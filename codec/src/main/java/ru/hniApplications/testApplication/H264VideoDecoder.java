package ru.hniApplications.testApplication;

import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swscale.*;
import org.bytedeco.javacpp.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.*;

import static org.bytedeco.ffmpeg.avcodec.AVCodecContext.FF_THREAD_SLICE;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

public class H264VideoDecoder implements AutoCloseable {

    private final int width;
    private final int height;

    private AVCodecContext codecCtx;
    private SwsContext swsCtx;
    private AVFrame decodedFrame;
    private AVFrame bgrFrame;
    private BytePointer bgrBuffer;
    private AVPacket packet;


    private final ArrayBlockingQueue<BufferedImage> frameQueue =
            new ArrayBlockingQueue<>(60);


    private final LinkedBlockingQueue<byte[]> inputQueue =
            new LinkedBlockingQueue<>(120);

    private volatile boolean running = true;
    private final Thread decodeThread;

    public H264VideoDecoder(int width, int height) {
        this.width = width;
        this.height = height;

        initCodec();

        decodeThread = new Thread(this::decodeLoop, "h264-decoder");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    private void initCodec() {
        AVCodec codec = avcodec_find_decoder(AV_CODEC_ID_H264);
        if (codec == null) {
            throw new RuntimeException("H.264 decoder not found");
        }

        codecCtx = avcodec_alloc_context3(codec);
        if (codecCtx == null) {
            throw new RuntimeException("Failed to create decoder context");
        }

        codecCtx.flags(codecCtx.flags() | AV_CODEC_FLAG_LOW_DELAY);
        codecCtx.flags2(codecCtx.flags2() | AV_CODEC_FLAG2_FAST);
        codecCtx.thread_count(Runtime.getRuntime().availableProcessors());
        codecCtx.thread_type(FF_THREAD_SLICE);

        if (avcodec_open2(codecCtx, codec, (AVDictionary) null) < 0) {
            throw new RuntimeException("Failed to open decoder");
        }

        decodedFrame = av_frame_alloc();
        bgrFrame = av_frame_alloc();
        packet = av_packet_alloc();

        int bgrSize = av_image_get_buffer_size(AV_PIX_FMT_BGR24, width, height, 1);
        bgrBuffer = new BytePointer(av_malloc(bgrSize)).capacity(bgrSize);

        av_image_fill_arrays(bgrFrame.data(), bgrFrame.linesize(),
                bgrBuffer, AV_PIX_FMT_BGR24, width, height, 1);

        bgrFrame.width(width);
        bgrFrame.height(height);
        bgrFrame.format(AV_PIX_FMT_BGR24);
    }

    private void decodeLoop() {
        while (running) {
            try {

                byte[] data = inputQueue.poll(10, TimeUnit.MILLISECONDS);
                if (data == null) continue;


                decodeChunk(data);

                java.util.ArrayList<byte[]> batch = new java.util.ArrayList<>();
                inputQueue.drainTo(batch);
                for (byte[] chunk : batch) {
                    decodeChunk(chunk);
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void decodeChunk(byte[] data) {
        BytePointer dataPtr = new BytePointer(data);
        packet.data(dataPtr);
        packet.size(data.length);

        int ret = avcodec_send_packet(codecCtx, packet);
        dataPtr.close();

        if (ret < 0) return;

        while (ret >= 0) {
            ret = avcodec_receive_frame(codecCtx, decodedFrame);
            if (ret < 0) break;

            BufferedImage img = frameToImage();
            if (img != null) {


                try {
                    boolean offered = frameQueue.offer(img, 100, TimeUnit.MILLISECONDS);
                    if (!offered) {
                        System.err.println("[Decoder] frameQueue overflow, frame dropped");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private BufferedImage frameToImage() {
        int srcW = decodedFrame.width();
        int srcH = decodedFrame.height();
        int srcFmt = decodedFrame.format();

        if (srcW <= 0 || srcH <= 0) return null;

        if (swsCtx == null) {
            swsCtx = sws_getContext(
                    srcW, srcH, srcFmt,
                    width, height, AV_PIX_FMT_BGR24,
                    SWS_BILINEAR,
                    null, null, (DoublePointer) null
            );
            if (swsCtx == null) {
                System.err.println("[Decoder] Failed to create SwsContext");
                return null;
            }
        }

        sws_scale(swsCtx,
                decodedFrame.data(),
                decodedFrame.linesize(),
                0, srcH,
                bgrFrame.data(),
                bgrFrame.linesize()
        );

        BufferedImage img = new BufferedImage(
                width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] imgData = ((DataBufferByte) img.getRaster()
                .getDataBuffer()).getData();

        int linesize = bgrFrame.linesize(0);
        BytePointer pixels = bgrFrame.data(0);

        if (linesize == width * 3) {
            pixels.get(imgData, 0, width * height * 3);
        } else {
            for (int y = 0; y < height; y++) {
                pixels.position((long) y * linesize)
                        .get(imgData, y * width * 3, width * 3);
            }
        }

        return img;
    }

    public void feedData(byte[] data) {
        if (data == null || data.length == 0) return;
        if (running) {

            if (!inputQueue.offer(data)) {
                System.err.println("[Decoder] inputQueue full, packet dropped");
            }
        }
    }

    public BufferedImage getLatestFrame() {
        BufferedImage latest = null;
        BufferedImage frame;
        while ((frame = frameQueue.poll()) != null) {
            latest = frame;
        }
        return latest;
    }

    public BufferedImage waitForFrame(long timeoutMs) throws InterruptedException {
        return frameQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }


    public int drainFrames(java.util.Collection<BufferedImage> dest) {
        return frameQueue.drainTo(dest);
    }

    public int getQueuedFrameCount() {
        return frameQueue.size();
    }

    public int getOutputWidth() {
        return width;
    }

    public int getOutputHeight() {
        return height;
    }

    @Override
    public void close() {
        running = false;
        try {
            decodeThread.interrupt();
            decodeThread.join(2000);
        } catch (InterruptedException ignored) {
        }

        if (swsCtx != null) {
            sws_freeContext(swsCtx);
            swsCtx = null;
        }
        if (packet != null) {
            av_packet_free(packet);
            packet = null;
        }
        if (decodedFrame != null) {
            av_frame_free(decodedFrame);
            decodedFrame = null;
        }
        if (bgrFrame != null) {
            av_frame_free(bgrFrame);
            bgrFrame = null;
        }
        if (bgrBuffer != null) {
            av_free(bgrBuffer);
            bgrBuffer = null;
        }
        if (codecCtx != null) {
            avcodec_free_context(codecCtx);
            codecCtx = null;
        }
    }
}