package ru.hniApplications.testApplication;

public class EncoderConfig {

    private final int width;
    private final int height;
    private final int fps;
    private final int bitrate;
    private final int gopSize;
    private final String profile;

    private EncoderConfig(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.fps = builder.fps;
        this.bitrate = builder.bitrate;
        this.gopSize = builder.gopSize;
        this.profile = builder.profile;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFps() {
        return fps;
    }

    public int getBitrate() {
        return bitrate;
    }

    public int getGopSize() {
        return gopSize;
    }

    public String getProfile() {
        return profile;
    }

    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    public static class Builder {
        private final int width;
        private final int height;
        private int fps = 30;
        private int bitrate = 3_000_000;
        private int gopSize = 60;
        private String profile = "baseline";

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public Builder fps(int fps) {
            this.fps = fps;
            return this;
        }

        public Builder bitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        public Builder gopSize(int gopSize) {
            this.gopSize = gopSize;
            return this;
        }

        public Builder profile(String profile) {
            this.profile = profile;
            return this;
        }

        public EncoderConfig build() {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width and height must be > 0");
            }
            if (fps < 1 || fps > 120) {
                throw new IllegalArgumentException("fps must be 1-120");
            }
            return new EncoderConfig(this);
        }
    }
}
