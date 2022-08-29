import java.util.List;

public class Request {
    private String method;
    private List<String> headers;
    private String body;

    public Request(String method, List<String> headers, String body) {
        this.method = method;
        this.headers = headers;
        this.body = body;
    }

    public String toString() {
        return "Метод запроса:" + method + "\n\n"+
                "Заголовки:" + headers.toString() + "\n\n" +
                "Тело запрса:" + body;

    }
}
