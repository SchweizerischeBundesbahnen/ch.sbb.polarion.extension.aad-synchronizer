package ch.sbb.polarion.extension.aad.synchronizer.connector;

public class UrlBuilder {
    private static final String VERSION = "v1.0";

    public String build(String baseUrl, GraphOption option) {

        return String.format("%s/%s/%s", baseUrl, VERSION, option.toString().toLowerCase());
    }

    public String build(String baseUrl, GraphOption option, String path) {

        String basePath = build(baseUrl, option);
        return path == null || path.isEmpty() ? basePath : String.format("%s/%s", basePath, path);
    }
}
