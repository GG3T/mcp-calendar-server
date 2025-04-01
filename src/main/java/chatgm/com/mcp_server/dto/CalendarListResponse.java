package chatgm.com.mcp_server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO para a resposta da API do Google Calendar
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarListResponse {
    private String kind;
    private String etag;
    private String nextPageToken;
    private List<CalendarItem> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalendarItem {
        private String kind;
        private String etag;
        private String id;
        private String summary;
        private String description;
        private String timeZone;
        private String colorId;
        private String backgroundColor;
        private String foregroundColor;
        private boolean selected;
        private String accessRole;
        private boolean primary;
    }
}
