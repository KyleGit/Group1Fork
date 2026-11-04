package com.napier.sem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

/** Generates population reports from the supplied world database. */
public class App {
    private Connection con;

    public App() {
    }

    // Allows database-independent tests to exercise JDBC resource handling.
    App(Connection connection) {
        con = connection;
    }

    /**
     * Runs the existing demonstration reports.
     * @param args optional database host:port and retry delay in milliseconds
     */
    public static void main(String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: java -jar semApp.jar [host:port] [retry-delay-ms]");
        }
        String address = args.length > 0 ? args[0] : environment("DB_ADDRESS", "db:3306");
        int delay = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        App a = new App();
        try {
            a.connect(address, delay);
            a.runReports();
        } finally {
            a.disconnect();
        }
    }

    private void runReports() {
        App a = this;
        // Gets and prints all countries in the world
        ArrayList<Country> allCountries = a.getAllCountries();
        a.printCountries(allCountries);

        // Gets and prints all countries in a continent
        String uContinent = "Europe";
        ArrayList<Country> continentCountries = a.getCountriesByContinent(uContinent);
        a.printCountries(continentCountries);

        // Gets and prints all countries in a region
        String uRegion = "Caribbean";
        ArrayList<Country> regionCountries = a.getCountriesByRegion(uRegion);
        a.printCountries(regionCountries);

        // Gets and prints the top n countries in the world
        int n1 = 3;
        ArrayList<Country> allNCountries = a.getTopCountries(n1);
        a.printCountries(allNCountries);

        // Gets and prints the top n countries in a continent
        String uNContinent = "Asia";
        int n2 = 4;
        ArrayList<Country> continentNCountries = a.getTopCountriesByContinent(uNContinent, n2);
        a.printCountries(continentNCountries);

        // Gets and prints the top n countries in a region
        String uNRegion = "Polynesia";
        int n3 = 4;
        ArrayList<Country> regionNCountries = a.getTopCountriesByRegion(uNRegion, n3);
        a.printCountries(regionNCountries);

        // Gets and prints all cities
        ArrayList<City> allCities = a.getAllCities();
        a.printCities(allCities);

        // Gets and prints all cities in a continent
        String uCContinent = "Africa";
        ArrayList<City> continentCities = a.getCitiesByContinent(uCContinent);
        a.printCities(continentCities);

        // Gets and prints all cities in a region
        String uCRegion = "Middle East";
        ArrayList<City> regionCities = a.getCitiesByRegion(uCRegion);
        a.printCities(regionCities);

        // Gets and prints all cities in a country
        String uCCountry = "Belarus";
        ArrayList<City> countryCities = a.getCitiesByCountry(uCCountry);
        a.printCities(countryCities);

        // Gets and prints all cities in a district
        String uCDistrict = "Zuid-Holland";
        ArrayList<City> districtCities = a.getCitiesByDistrict(uCDistrict);
        a.printCities(districtCities);

        // Gets and prints top cities in the world
        int n4 = 5;
        ArrayList<City> topCities = a.getTopCities(n4);
        a.printCities(topCities);

        // Gets and prints the top cities in a continent
        int n5 = 4;
        String uCityContinent = "South America";
        ArrayList<City> topContinentCities = a.getTopCitiesByContinent(uCityContinent, n5);
        a.printCities(topContinentCities);

        // Gets and prints the top cities in a region
        int n6 = 6;
        String uCityRegion = "Southern and Central Asia";
        ArrayList<City> topRegionCities = a.getTopCitiesByRegion(uCityRegion, n6);
        a.printCities(topRegionCities);

        // Gets and prints the top cities in a country
        int n7 = 20;
        String uCityCountry = "United Kingdom";
        ArrayList<City> topCountryCities = a.getTopCitiesByCountry(uCityCountry, n7);
        a.printCities(topCountryCities);

        // Gets and prints tha top cities in a district
        int n8 = 6;
        String uCityDistrict = "Buenos Aires";
        ArrayList<City> topDistrictCities = a.getTopCitiesByDistrict(uCityDistrict, n8);
        a.printCities(topDistrictCities);

        // Get all capital cities in the world
        ArrayList<City> allCapitalCities = a.getAllCapitalCities();
        a.printCapCities(allCapitalCities);

        // Get all capital cities in a continent
        String uCapContinent = "Africa";
        ArrayList<City> allContinentCapitalCities = a.getCapCitiesByContinent(uCapContinent);
        a.printCapCities(allContinentCapitalCities);

        // Get all capital cities in a region
        String uCapRegion = "British Islands";
        ArrayList<City> allRegionCapitalCities = a.getCapCitiesByRegion(uCapRegion);
        a.printCapCities(allRegionCapitalCities);

        // Get the top n capital cities in the world
        int n9 = 10;
        ArrayList<City> topCapitalCities = a.getTopCapCities(n9);
        a.printCapCities(topCapitalCities);

        // Get the top n capital cities in a continent
        int n10 = 7;
        String uNCapContinent = "Europe";
        ArrayList<City> topCapContinentCities = a.getTopCapCitiesByContinent(uNCapContinent, n10);
        a.printCapCities(topCapContinentCities);

        // Get the top n capital cities in a region
        int n11 = 8;
        String uNCapCityRegion = "Western Europe";
        ArrayList<City> topCapRegionCities = a.getTopCapCitiesByRegion(uNCapCityRegion, n11);
        a.printCapCities(topCapRegionCities);

    }

    /** Connects using DB_ADDRESS, or the Docker database service by default. */
    public void connect() {
        connect(environment("DB_ADDRESS", "db:3306"), 1000);
    }

    /**
     * Connects to the world database, retrying while the database starts.
     * DB_USER and DB_PASSWORD override the demonstration credentials.
     * @param location database host:port, for example localhost:33060
     * @param delayMillis delay between failed attempts; zero retries immediately
     */
    public void connect(String location, int delayMillis) {
        connect(location, delayMillis, 30);
    }

    void connect(String location, int delayMillis, int attempts) {
        requireFilter(location, "Database address");
        if (!location.matches("[A-Za-z0-9._-]+:[0-9]{1,5}")) {
            throw new IllegalArgumentException("Database address must be host:port");
        }
        int port = Integer.parseInt(location.substring(location.lastIndexOf(':') + 1));
        if (port < 1 || port > 65535 || delayMillis < 0 || attempts < 1) {
            throw new IllegalArgumentException("Invalid database port, retry delay, or attempt count");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Database connection interrupted");
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load MySQL driver", e);
        }

        disconnect();
        SQLException failure = null;
        String url = "jdbc:mysql://" + location
                + "/world?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                con = DriverManager.getConnection(url,
                        environment("DB_USER", "root"), environment("DB_PASSWORD", "example"));
                return;
            } catch (SQLException e) {
                failure = e;
                if (attempt < attempts) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Database connection interrupted", interrupted);
                    }
                }
            }
        }
        throw new IllegalStateException("Could not connect to database at " + location
                + " after " + attempts + " attempts", failure);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Closes the connection. Calling this again is safe. */
    public void disconnect() {
        if (con != null) {
            Connection connection = con;
            con = null;
            try {
                connection.close();
            } catch (SQLException e) {
                throw new IllegalStateException("Could not close database connection", e);
            }
        }
    }

    /** All report filters must identify a nonempty place. */
    private static String requireFilter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        return value;
    }

    /** A top-N report requires at least one row. */
    private static int requireLimit(Integer n) {
        if (n == null || n <= 0) {
            throw new IllegalArgumentException("Number of results must be positive");
        }
        return n;
    }

    private static final String COUNTRY_SELECT =
            "SELECT country.Code, country.Name, country.Continent, country.Region, "
            + "country.Population, city.Name AS Capital FROM country "
            + "LEFT JOIN city ON city.ID = country.Capital ";

    private static final String CITY_SELECT =
            "SELECT city.Name, country.Name AS Country_name, city.District, city.Population "
            + "FROM city JOIN country ON city.CountryCode = country.Code ";

    private static final String CAPITAL_SELECT =
            "SELECT city.Name, country.Name AS Country_name, city.District, city.Population "
            + "FROM city JOIN country ON city.ID = country.Capital ";

    /** Returns all countries in the world, ordered by descending population. */
    public ArrayList<Country> getAllCountries() {
        return query(COUNTRY_SELECT + "ORDER BY country.Population DESC, country.Code",
                App::readCountry);
    }

    /** Returns countries in a continent, ordered by descending population. */
    public ArrayList<Country> getCountriesByContinent(String continent) {
        requireFilter(continent, "Continent");
        return query(COUNTRY_SELECT + "WHERE country.Continent = ? " + "ORDER BY country.Population DESC, country.Code",
                App::readCountry, continent);
    }

    /** Returns countries in a region, ordered by descending population. */
    public ArrayList<Country> getCountriesByRegion(String region) {
        requireFilter(region, "Region");
        return query(COUNTRY_SELECT + "WHERE country.Region = ? " + "ORDER BY country.Population DESC, country.Code",
                App::readCountry, region);
    }

    /** Returns the top N countries in the world, ordered by descending population. */
    public ArrayList<Country> getTopCountries(Integer n) {
        requireLimit(n);
        return query(COUNTRY_SELECT + "ORDER BY country.Population DESC, country.Code LIMIT ?",
                App::readCountry, n);
    }

    /** Returns the top N countries in a continent, ordered by descending population. */
    public ArrayList<Country> getTopCountriesByContinent(String continent, Integer n) {
        requireFilter(continent, "Continent");
        requireLimit(n);
        return query(COUNTRY_SELECT + "WHERE country.Continent = ? " + "ORDER BY country.Population DESC, country.Code LIMIT ?",
                App::readCountry, continent, n);
    }

    /** Returns the top N countries in a region, ordered by descending population. */
    public ArrayList<Country> getTopCountriesByRegion(String region, Integer n) {
        requireFilter(region, "Region");
        requireLimit(n);
        return query(COUNTRY_SELECT + "WHERE country.Region = ? " + "ORDER BY country.Population DESC, country.Code LIMIT ?",
                App::readCountry, region, n);
    }

    /** Returns all cities in the world, ordered by descending population. */
    public ArrayList<City> getAllCities() {
        return query(CITY_SELECT + "ORDER BY city.Population DESC, city.ID",
                App::readCity);
    }

    /** Returns cities in a continent, ordered by descending population. */
    public ArrayList<City> getCitiesByContinent(String continent) {
        requireFilter(continent, "Continent");
        return query(CITY_SELECT + "WHERE country.Continent = ? " + "ORDER BY city.Population DESC, city.ID",
                App::readCity, continent);
    }

    /** Returns cities in a region, ordered by descending population. */
    public ArrayList<City> getCitiesByRegion(String region) {
        requireFilter(region, "Region");
        return query(CITY_SELECT + "WHERE country.Region = ? " + "ORDER BY city.Population DESC, city.ID",
                App::readCity, region);
    }

    /** Returns cities in a country, ordered by descending population. */
    public ArrayList<City> getCitiesByCountry(String country) {
        requireFilter(country, "Country");
        return query(CITY_SELECT + "WHERE country.Name = ? " + "ORDER BY city.Population DESC, city.ID",
                App::readCity, country);
    }

    /** Returns cities in a district, ordered by descending population. */
    public ArrayList<City> getCitiesByDistrict(String district) {
        requireFilter(district, "District");
        return query(CITY_SELECT + "WHERE city.District = ? " + "ORDER BY city.Population DESC, city.ID",
                App::readCity, district);
    }

    /** Returns the top N cities in the world, ordered by descending population. */
    public ArrayList<City> getTopCities(Integer n) {
        requireLimit(n);
        return query(CITY_SELECT + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, n);
    }

    /** Returns the top N cities in a continent, ordered by descending population. */
    public ArrayList<City> getTopCitiesByContinent(String continent, Integer n) {
        requireFilter(continent, "Continent");
        requireLimit(n);
        return query(CITY_SELECT + "WHERE country.Continent = ? " + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, continent, n);
    }

    /** Returns the top N cities in a region, ordered by descending population. */
    public ArrayList<City> getTopCitiesByRegion(String region, Integer n) {
        requireFilter(region, "Region");
        requireLimit(n);
        return query(CITY_SELECT + "WHERE country.Region = ? " + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, region, n);
    }

    /** Returns the top N cities in a country, ordered by descending population. */
    public ArrayList<City> getTopCitiesByCountry(String country, Integer n) {
        requireFilter(country, "Country");
        requireLimit(n);
        return query(CITY_SELECT + "WHERE country.Name = ? " + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, country, n);
    }

    /** Returns the top N cities in a district, ordered by descending population. */
    public ArrayList<City> getTopCitiesByDistrict(String district, Integer n) {
        requireFilter(district, "District");
        requireLimit(n);
        return query(CITY_SELECT + "WHERE city.District = ? " + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, district, n);
    }

    /** Returns all capital cities in the world, ordered by descending population. */
    public ArrayList<City> getAllCapitalCities() {
        return query(CAPITAL_SELECT + "ORDER BY city.Population DESC, city.ID",
                App::readCity);
    }

    /** Returns capital cities in a continent, ordered by descending population. */
    public ArrayList<City> getCapCitiesByContinent(String continent) {
        requireFilter(continent, "Continent");
        return query(CAPITAL_SELECT + "WHERE country.Continent = ? " + "ORDER BY city.Population DESC, city.ID",
                App::readCity, continent);
    }

    /** Returns capital cities in a region, ordered by descending population. */
    public ArrayList<City> getCapCitiesByRegion(String region) {
        requireFilter(region, "Region");
        return query(CAPITAL_SELECT + "WHERE country.Region = ? " + "ORDER BY city.Population DESC, city.ID",
                App::readCity, region);
    }

    /** Returns the top N capital cities in the world, ordered by descending population. */
    public ArrayList<City> getTopCapCities(Integer n) {
        requireLimit(n);
        return query(CAPITAL_SELECT + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, n);
    }

    /** Returns the top N capital cities in a continent, ordered by descending population. */
    public ArrayList<City> getTopCapCitiesByContinent(String continent, Integer n) {
        requireFilter(continent, "Continent");
        requireLimit(n);
        return query(CAPITAL_SELECT + "WHERE country.Continent = ? " + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, continent, n);
    }

    /** Returns the top N capital cities in a region, ordered by descending population. */
    public ArrayList<City> getTopCapCitiesByRegion(String region, Integer n) {
        requireFilter(region, "Region");
        requireLimit(n);
        return query(CAPITAL_SELECT + "WHERE country.Region = ? " + "ORDER BY city.Population DESC, city.ID LIMIT ?",
                App::readCity, region, n);
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T read(ResultSet result) throws SQLException;
    }

    /** Binds user input as values and closes both JDBC resources on every path. */
    private <T> ArrayList<T> query(String sql, RowMapper<T> mapper, Object... values) {
        if (con == null) {
            throw new IllegalStateException("Connect to the database before requesting reports");
        }
        try (PreparedStatement statement = con.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                ArrayList<T> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(mapper.read(result));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not generate report", e);
        }
    }

    private static Country readCountry(ResultSet result) throws SQLException {
        Country country = new Country();
        country.code = result.getString("Code");
        country.name = result.getString("Name");
        country.continent = result.getString("Continent");
        country.region = result.getString("Region");
        country.population = result.getInt("Population");
        country.capital = result.getString("Capital");
        return country;
    }

    private static City readCity(ResultSet result) throws SQLException {
        City city = new City();
        city.name = result.getString("Name");
        city.country_name = result.getString("Country_name");
        city.district = result.getString("District");
        city.population = result.getInt("Population");
        return city;
    }

    private static boolean hasRows(ArrayList<?> rows) {
        return rows != null && rows.stream().anyMatch(Objects::nonNull);
    }

    private static String display(String text) {
        return text == null ? "-" : text;
    }

    /** Prints the required country columns, ignoring missing list members. */
    public void printCountries(ArrayList<Country> countries) {
        if (!hasRows(countries)) {
            System.out.println("No countries in list.");
            return;
        }
        String format = "%-8s %-40s %-15s %-30s %-15s %-10s%n";
        System.out.printf(format, "Code", "Name", "Continent", "Region", "Population", "Capital");
        for (Country country : countries) {
            if (country != null) {
                System.out.printf(format, display(country.code), display(country.name),
                        display(country.continent), display(country.region), country.population,
                        display(country.capital));
            }
        }
    }

    /** Prints the required city columns, ignoring missing list members. */
    public void printCities(ArrayList<City> cities) {
        if (!hasRows(cities)) {
            System.out.println("No cities in list.");
            return;
        }
        String format = "%-40s %-40s %-20s %-20s%n";
        System.out.printf(format, "Name", "Country", "District", "Population");
        for (City city : cities) {
            if (city != null) {
                System.out.printf(format, display(city.name), display(city.country_name),
                        display(city.district), city.population);
            }
        }
    }

    /** Prints the required capital columns, ignoring missing list members. */
    public void printCapCities(ArrayList<City> cities) {
        if (!hasRows(cities)) {
            System.out.println("No cities in list.");
            return;
        }
        String format = "%-40s %-40s %-20s%n";
        System.out.printf(format, "Name", "Country", "Population");
        for (City city : cities) {
            if (city != null) {
                System.out.printf(format, display(city.name), display(city.country_name), city.population);
            }
        }
    }
}
