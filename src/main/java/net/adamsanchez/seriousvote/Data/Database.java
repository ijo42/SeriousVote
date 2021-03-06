package net.adamsanchez.seriousvote.Data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.adamsanchez.seriousvote.SeriousVote;
import net.adamsanchez.seriousvote.utils.U;

import java.sql.*;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Created by adam_ on 01/22/17.
 */
public class Database {
    private String playerTable = "players";
    final HikariConfig config = new HikariConfig();
    private ArrayList<PlayerRecord> recordCache;
    private long recordCacheExpirationTime;
    final HikariDataSource ds;
    private final String timezoneFix = "&useUnicode=true&useLegacyDatetimeCode=false&serverTimezone=UTC";


    public Database() {
        SeriousVote sv = SeriousVote.getInstance();
        String host = sv.databaseHostname;
        String port = sv.databasePort;
        String dbname = sv.databaseName;
        String table_prefix = sv.databasePrefix;
        String username = sv.databaseUsername;
        String password = sv.databasePassword;
        String dbType1 = "mysql";
        String dbType = sv.databaseType == null || Objects.equals(sv.databaseType, "") ? dbType1 : sv.databaseType.toLowerCase();
        dbType = "mariadb";
        int maxActiveConnections = 4;
        int minIdleConnections = 2;
        try {
            minIdleConnections = Integer.parseInt(sv.minIdleConnections);
            maxActiveConnections = Integer.parseInt(sv.maxActiveConnections);
        } catch (Exception e) {
            U.info("Incorrect values given for connection pool, reverting to default");
            U.info("Max Active: " + maxActiveConnections);
            U.info("Min Idle: " + minIdleConnections);
        }
        playerTable = table_prefix + "players";

        String url = "jdbc:" + dbType + "://" + host + ":" + port + "/" + dbname + "?useSSL=false";

        config.setJdbcUrl(url + timezoneFix);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxActiveConnections);
        config.setMinimumIdle(minIdleConnections);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1770000);
        config.setPoolName("SeriousVote-SQL");


        U.debug("Attempting connection to: " + url);
        //Instantiate Pool
        ds = new HikariDataSource(config);
        U.info("Ready for connections");
    }

    public Database(String url, String username, String password) {
        config.setJdbcUrl(url + timezoneFix);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1770000);
        config.setPoolName("SeriousVote-SQL");

        ds = new HikariDataSource(config);
        U.info("Ready for connections");
    }


    /////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////


    public void shutdown() {
        ds.close();
    }

    public Connection getConnection() throws SQLException {
        U.debug("Establishing connection with the database.");
        return ds.getConnection();
    }


    public PreparedStatement preparedStatement(Connection con, String string) {
        PreparedStatement statement = null;
        try {
            statement = con.prepareStatement(string);
        } catch (SQLException e) {
            U.error("Error in DB Connection");
        }
        return statement;
    }

    private ResultSet genericQuery(Connection con, String query) {
        U.debug("Processing query...");
        ResultSet results = null;
        try {
            results = con.createStatement().executeQuery(query);
        } catch (SQLException e) {
            U.error("Error running query!", e);
        }
        return results;
    }


    public ResultSet genericSelectQuery(Connection con, String table, String field, String value) {
        String initial = "SELECT * FROM %s WHERE %s='%s'";
        return genericQuery(con, String.format(initial, table, field, value));
    }

    /**
     * Gets an ordered list of records starting at the nth record (offset) using no Limit
     *
     * @param con
     * @param table
     * @param orderByField
     * @return
     */
    public ResultSet orderedSelectQuery(Connection con, String table, String orderByField) {
        String initial = "SELECT * FROM %s ORDER BY %s DESC";
        return genericQuery(con, String.format(initial, table, orderByField));
    }

    /**
     * Gets an ordered list of records starting at the nth record (offset) with a limit
     *
     * @param con
     * @param table
     * @param orderByField
     * @param limit
     * @param offset
     * @return
     */
    public ResultSet orderedSelectQuery(Connection con, String table, String orderByField, int limit, int offset) {
        String initial = "SELECT * FROM %s ORDER BY %s DESC LIMIT %s OFFSET %s";
        return genericQuery(con, String.format(initial, table, orderByField, limit, offset));
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    public PlayerRecord getPlayer(String playerIdentifier) {
        U.debug("Attempting to retrieve player data from database....");
        try (Connection con = getConnection(); ResultSet results = genericSelectQuery(con, playerTable, "player", playerIdentifier)) {
            if (results.first()) {
                int sequentialVotes = results.getInt("voteSpree");
                Date lastVote = results.getDate("lastVote");
                int totalVote = results.getInt("totalVotes");
                return new PlayerRecord(playerIdentifier, totalVote, sequentialVotes, lastVote);
            }
        } catch (SQLException e) {
            U.error("Trouble getting information from the database");
        }
        return null;
    }

    /**
     * Retrieves the nth row from the cache if it exists
     *
     * @param rank The n'th record
     * @return
     */
    public PlayerRecord getRecordByRank(int rank) {
        U.debug("Attempting to retrieve record by rank from cache...");
        ArrayList<PlayerRecord> cache = getAllRecords();
        if (rank < cache.size()) {
            return cache.get(rank);
        } else {
            U.debug("There are not that many records!!! Requsted " + (rank + 1));
            return null;
        }
    }

    public void updatePlayer(PlayerRecord player) {
        playerUpdateQuery(this.playerTable, player.getPlayerIdentifier(), player.getTotalVotes(), player.getVoteSpree(), player.getLastVote());
    }

    /**
     * Sets all player votes to 0
     */
    public void resetPlayers() {
        U.debug("Attempting to remove all players....");
        String query = String.format("UPDATE %s SET totalVotes = 0", playerTable);
        try (Connection con = getConnection(); PreparedStatement statement = preparedStatement(con, query)) {
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deletePlayer(String playerIdentifier) {
        U.debug("Attempting to remove player from database...");
        String query = String.format("DELETE FROM %s WHERE player='%s';", playerTable, playerIdentifier);

        try (Connection con = getConnection(); PreparedStatement statement = preparedStatement(con, query)) {
            statement.execute();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public ArrayList<PlayerRecord> getAllRecords() {
        //First Check Cache
        //Has it been initialized
        U.debug("Cache Expires at: " + recordCacheExpirationTime + " Current Time: " + new java.util.Date().getTime());

        long cacheLifetime = 10000L;
        if (recordCache == null || recordCache.size() == 0) {
            U.debug("Cache initializing....");
            recordCacheExpirationTime = new java.util.Date().getTime() + cacheLifetime;
            return updateAllPlayerCache();
        } else {
            U.debug("Checking cache expiration");
            //If time has expired
            if (recordCacheExpirationTime < new java.util.Date().getTime()) {
                recordCacheExpirationTime = new java.util.Date().getTime() + cacheLifetime;
                U.debug("Refreshing cache");
                return updateAllPlayerCache();
            } else {
                U.debug("Cache still fresh...");
                return recordCache;
            }
        }
    }

    private ArrayList<PlayerRecord> updateAllPlayerCache() {
        U.debug("Attempting to update player cache...");
        ArrayList<PlayerRecord> recordList = new ArrayList<>();


        try (Connection con = getConnection(); ResultSet results = orderedSelectQuery(con, playerTable, "totalVotes")) {
            U.debug("Received " + results.getFetchSize());
            while (results.next()) {
                int sequentialVotes = results.getInt("voteSpree");
                Date lastVote = results.getDate("lastVote");
                int totalVote = results.getInt("totalVotes");
                String playerIdentifier = results.getString("player");
                recordList.add(new PlayerRecord(playerIdentifier, totalVote, sequentialVotes, lastVote));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        recordCache = recordList;
        return recordCache;
    }

    public void playerUpdateQuery(String table, String playerIdentifier, int totalVotes, int voteSpree, Date lastVote) {
        U.debug("Attempting to update db...");
        String initial = "REPLACE INTO %s(player, totalVotes, voteSpree, lastVote) VALUES(?,?,?,?)";
        PreparedStatement statement = null;
        Connection con = null;

        try {
            con = getConnection();
            U.debug("Connection established...");
            statement = preparedStatement(con, String.format(initial, table));
            statement.setString(1, playerIdentifier);
            statement.setInt(2, totalVotes);
            statement.setInt(3, voteSpree);
            statement.setDate(4, lastVote);
            statement.execute();
            U.debug("Statement Executed....");
        } catch (SQLException e) {
            U.error("Error in trying to update player vote record!");
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void createPlayerTable() {
        U.debug("Initializing table...");
        String table = String.format("CREATE TABLE IF NOT EXISTS %s(" +
                "player			VarChar(36) PRIMARY KEY," +
                "lastVote		DATE," +
                "totalVotes		INT," +
                "voteSpree		INT" +
                ")", playerTable);
        try (Connection con = getConnection(); Statement statement = con.createStatement()) {
            statement.executeUpdate(table);
        } catch (SQLException e) {
            U.error("Error Creating SQL TABLE-- CHECK YOUR DATA CONFIG", e);
        }

    }

    /**
     * Returns the number of players in the table
     */
    public int getCount() {
        U.debug("GETTING COUNT");
        ResultSet results = null;
        Connection con = null;
        Statement statement = null;

        int count = 0;
        String query = String.format("SELECT COUNT(*) FROM %s;", playerTable);
        try {
            con = getConnection();
            results = con.createStatement().executeQuery(query);
            if (results.next()) {
                count = results.getInt(1);
                U.debug("Table has " + count + " players.");
                return count;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (results != null) {
                    results.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception ignored) {
            }
        }
        return count;
    }

}
