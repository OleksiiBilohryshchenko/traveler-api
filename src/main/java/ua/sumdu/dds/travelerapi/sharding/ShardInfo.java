package ua.sumdu.dds.travelerapi.sharding;

public record ShardInfo(
        String host,
        int port,
        String database,
        String user,
        String password
) {}
