package ua.sumdu.dds.travelerapi.service;

import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthCheckService {

    private final DataSource dataSource;
    private final long startTime = System.currentTimeMillis();

    public HealthCheckService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> checkDatabase() {
        Map<String, Object> data = new HashMap<>();
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            data.put("status", "healthy");
        } catch (Exception e) {
            data.put("status", "unhealthy");
        }
        data.put("responseTime", System.currentTimeMillis() - start);
        return data;
    }

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
}
