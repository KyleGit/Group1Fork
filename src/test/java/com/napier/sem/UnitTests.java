package com.napier.sem;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests run without MySQL; integration tests validate real SQL results. */
public class UnitTests {
    private final App app = new App();

    @TestFactory
    Stream<DynamicTest> filtersRejectMissingValuesBeforeAccessingDatabase() {
        Map<String, Function<String, ?>> reports = new LinkedHashMap<>();
        reports.put("countries by continent", app::getCountriesByContinent);
        reports.put("countries by region", app::getCountriesByRegion);
        reports.put("top countries by continent", v -> app.getTopCountriesByContinent(v, 3));
        reports.put("top countries by region", v -> app.getTopCountriesByRegion(v, 3));
        reports.put("cities by continent", app::getCitiesByContinent);
        reports.put("cities by region", app::getCitiesByRegion);
        reports.put("cities by country", app::getCitiesByCountry);
        reports.put("cities by district", app::getCitiesByDistrict);
        reports.put("top cities by continent", v -> app.getTopCitiesByContinent(v, 3));
        reports.put("top cities by region", v -> app.getTopCitiesByRegion(v, 3));
        reports.put("top cities by country", v -> app.getTopCitiesByCountry(v, 3));
        reports.put("top cities by district", v -> app.getTopCitiesByDistrict(v, 3));
        reports.put("capitals by continent", app::getCapCitiesByContinent);
        reports.put("capitals by region", app::getCapCitiesByRegion);
        reports.put("top capitals by continent", v -> app.getTopCapCitiesByContinent(v, 3));
        reports.put("top capitals by region", v -> app.getTopCapCitiesByRegion(v, 3));
        return reports.entrySet().stream().map(r -> DynamicTest.dynamicTest(r.getKey(), () -> {
            for (String invalid : Arrays.asList(null, "", " \t ")) {
                assertThrows(IllegalArgumentException.class, () -> r.getValue().apply(invalid));
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> topReportsRejectNullZeroAndNegativeLimits() {
        Map<String, Function<Integer, ?>> reports = new LinkedHashMap<>();
        reports.put("top countries", app::getTopCountries);
        reports.put("top countries by continent", n -> app.getTopCountriesByContinent("Europe", n));
        reports.put("top countries by region", n -> app.getTopCountriesByRegion("Caribbean", n));
        reports.put("top cities", app::getTopCities);
        reports.put("top cities by continent", n -> app.getTopCitiesByContinent("Europe", n));
        reports.put("top cities by region", n -> app.getTopCitiesByRegion("Caribbean", n));
        reports.put("top cities by country", n -> app.getTopCitiesByCountry("France", n));
        reports.put("top cities by district", n -> app.getTopCitiesByDistrict("California", n));
        reports.put("top capitals", app::getTopCapCities);
        reports.put("top capitals by continent", n -> app.getTopCapCitiesByContinent("Europe", n));
        reports.put("top capitals by region", n -> app.getTopCapCitiesByRegion("Caribbean", n));
        return reports.entrySet().stream().map(r -> DynamicTest.dynamicTest(r.getKey(), () -> {
            for (Integer invalid : Arrays.asList(null, 0, -1)) {
                assertThrows(IllegalArgumentException.class, () -> r.getValue().apply(invalid));
            }
        }));
    }

    @Test
    void reportsRequireAConnection() {
        assertAll(() -> assertThrows(IllegalStateException.class, app::getAllCountries),
                () -> assertThrows(IllegalStateException.class, app::getAllCities),
                () -> assertThrows(IllegalStateException.class, app::getAllCapitalCities));
    }

    @Test
    void countryPrinterHandlesNullEmptyAndNullOnlyLists() {
        for (ArrayList<Country> input : Arrays.asList(null, new ArrayList<Country>(), list((Country) null))) {
            assertEquals("No countries in list." + System.lineSeparator(), output(() -> app.printCountries(input)));
        }
    }

    @Test
    void cityPrintersHandleNullEmptyAndNullOnlyLists() {
        for (ArrayList<City> input : Arrays.asList(null, new ArrayList<City>(), list((City) null))) {
            assertEquals("No cities in list." + System.lineSeparator(), output(() -> app.printCities(input)));
            assertEquals("No cities in list." + System.lineSeparator(), output(() -> app.printCapCities(input)));
        }
    }

    @Test
    void countryPrinterIncludesEveryRequiredColumnAndSkipsNullMembers() {
        Country france = new Country();
        france.code = "FRA";
        france.name = "France";
        france.continent = "Europe";
        france.region = "Western Europe";
        france.population = 59225700;
        france.capital = "Paris";
        String[] lines = output(() -> app.printCountries(list(null, france, null))).lines().toArray(String[]::new);
        assertEquals(2, lines.length);
        assertArrayEquals(new String[]{"Code", "Name", "Continent", "Region", "Population", "Capital"}, lines[0].trim().split("\\s+"));
        assertEquals("FRA France Europe Western Europe 59225700 Paris", normalize(lines[1]));
    }

    @Test
    void countryWithoutCapitalStillPrintsAPopulationRow() {
        Country country = new Country();
        country.code = "ATA";
        country.name = "Antarctica";
        country.continent = "Antarctica";
        country.region = "Antarctica";
        String printed = output(() -> app.printCountries(list(country)));
        assertEquals("ATA Antarctica Antarctica Antarctica 0 -", normalize(printed.lines().skip(1).findFirst().orElseThrow()));
        assertFalse(printed.contains("null"));
    }

    @Test
    void cityPrinterIncludesDistrictAndPreservesInputOrder() {
        City first = city("London", "United Kingdom", "England", 7285000);
        City second = city("Edinburgh", "United Kingdom", "Scotland", 450180);
        String[] lines = output(() -> app.printCities(list(first, null, second))).lines().toArray(String[]::new);
        assertEquals(3, lines.length);
        assertArrayEquals(new String[]{"Name", "Country", "District", "Population"}, lines[0].trim().split("\\s+"));
        assertEquals("London United Kingdom England 7285000", normalize(lines[1]));
        assertEquals("Edinburgh United Kingdom Scotland 450180", normalize(lines[2]));
    }

    @Test
    void capitalPrinterIncludesOnlyRequiredColumns() {
        City london = city("London", "United Kingdom", "England", 7285000);
        String[] lines = output(() -> app.printCapCities(list(null, london))).lines().toArray(String[]::new);
        assertEquals(2, lines.length);
        assertArrayEquals(new String[]{"Name", "Country", "Population"}, lines[0].trim().split("\\s+"));
        assertEquals("London United Kingdom 7285000", normalize(lines[1]));
        assertFalse(lines[1].contains("England"));
    }

    @Test
    void printersRenderMissingTextFieldsWithoutCrashing() {
        assertEquals("- - - - 0 -", normalize(output(() -> app.printCountries(list(new Country()))).lines().skip(1).findFirst().orElseThrow()));
        assertEquals("- - - 0", normalize(output(() -> app.printCities(list(new City()))).lines().skip(1).findFirst().orElseThrow()));
        assertEquals("- - 0", normalize(output(() -> app.printCapCities(list(new City()))).lines().skip(1).findFirst().orElseThrow()));
    }

    @Test
    void countryQueryMapsEveryFieldAndClosesResources() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.rows.add(Map.of("Code", "FRA", "Name", "France", "Continent", "Europe", "Region", "Western Europe", "Population", 59225700, "Capital", "Paris"));
        ArrayList<Country> countries = new App(jdbc.connection()).getAllCountries();
        assertEquals(1, countries.size());
        Country country = countries.get(0);
        assertAll(() -> assertEquals("FRA", country.code), () -> assertEquals("France", country.name),
                () -> assertEquals("Europe", country.continent), () -> assertEquals("Western Europe", country.region),
                () -> assertEquals(59225700, country.population), () -> assertEquals("Paris", country.capital),
                () -> assertTrue(jdbc.resultClosed), () -> assertTrue(jdbc.statementClosed));
        assertFalse(jdbc.connectionClosed);
    }

    @Test
    void cityQueryBindsApostrophesAndLimitAsDataAndMapsAllColumns() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.rows.add(Map.of("Name", "Abidjan", "Country_name", "Côte d'Ivoire", "District", "Abidjan", "Population", 2500000));
        ArrayList<City> cities = new App(jdbc.connection()).getTopCitiesByCountry("Côte d'Ivoire", 2);
        assertEquals(List.of("Côte d'Ivoire", 2), jdbc.values);
        assertFalse(jdbc.sql.contains("Côte d'Ivoire"));
        assertEquals(1, cities.size());
        City city = cities.get(0);
        assertAll(() -> assertEquals("Abidjan", city.name), () -> assertEquals("Côte d'Ivoire", city.country_name),
                () -> assertEquals("Abidjan", city.district), () -> assertEquals(2500000, city.population),
                () -> assertTrue(jdbc.resultClosed), () -> assertTrue(jdbc.statementClosed));
    }

    @Test
    void noMatchingRowsReturnsAnEmptyList() {
        JdbcStub jdbc = new JdbcStub();
        assertTrue(new App(jdbc.connection()).getCountriesByRegion("Unknown region").isEmpty());
        assertTrue(jdbc.resultClosed);
        assertTrue(jdbc.statementClosed);
    }

    @Test
    void queryFailureIsVisibleAndClosesStatement() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.executeFailure = new SQLException("Query failed");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new App(jdbc.connection()).getAllCities());
        assertSame(jdbc.executeFailure, failure.getCause());
        assertTrue(jdbc.statementClosed);
    }

    @Test
    void mappingFailureClosesBothResources() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.rows.add(Map.of("Name", "London"));
        jdbc.readFailure = new SQLException("Column unavailable");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new App(jdbc.connection()).getAllCapitalCities());
        assertSame(jdbc.readFailure, failure.getCause());
        assertTrue(jdbc.resultClosed);
        assertTrue(jdbc.statementClosed);
    }

    @Test
    void disconnectClosesAndClearsConnection() {
        JdbcStub jdbc = new JdbcStub();
        App connected = new App(jdbc.connection());
        connected.disconnect();
        connected.disconnect();
        assertTrue(jdbc.connectionClosed);
        assertEquals(1, jdbc.connectionCloseCount);
        assertThrows(IllegalStateException.class, connected::getAllCountries);
        assertDoesNotThrow(app::disconnect);
    }

    @Test
    void connectionSettingsRejectInvalidValuesWithoutNetworkAccess() {
        for (String address : Arrays.asList(null, "", " ", "localhost", "localhost:0", "localhost:65536", "localhost:3306/world?x=1", "localhost:abc")) {
            assertThrows(IllegalArgumentException.class, () -> app.connect(address, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> app.connect("localhost:33060", -1));
        assertThrows(IllegalArgumentException.class, () -> app.connect("localhost:33060", 0, 0));
    }

    @Test
    void interruptedConnectionDoesNotClearInterruptOrAttemptNetworkAccess() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> app.connect("localhost:33060", 0));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void connectionFailureStopsAfterTheConfiguredAttemptCount() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> app.connect("127.0.0.1:" + unusedPort, 0, 1));
        assertTrue(failure.getMessage().contains("after 1 attempts"));
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    @Test
    void commandLineRejectsExtraOrInvalidArgumentsBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> App.main(new String[]{"host:3306", "0", "extra"}));
        assertThrows(NumberFormatException.class, () -> App.main(new String[]{"localhost:33060", "invalid"}));
        assertThrows(IllegalArgumentException.class, () -> App.main(new String[]{"localhost:33060", "-1"}));
    }

    private static City city(String name, String country, String district, int population) {
        City city = new City();
        city.name = name;
        city.country_name = country;
        city.district = district;
        city.population = population;
        return city;
    }

    @SafeVarargs
    private static <T> ArrayList<T> list(T... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    private static String output(Runnable action) {
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            action.run();
        } finally {
            System.setOut(previous);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** A small JDBC test double records interactions without a database server. */
    private static class JdbcStub {
        final List<Map<String, Object>> rows = new ArrayList<>();
        final List<Object> values = new ArrayList<>();
        String sql;
        int position = -1;
        boolean resultClosed;
        boolean statementClosed;
        boolean connectionClosed;
        int connectionCloseCount;
        SQLException executeFailure;
        SQLException readFailure;

        Connection connection() {
            return proxy(Connection.class, (instance, method, args) -> {
                switch (method.getName()) {
                    case "prepareStatement": sql = (String) args[0]; return statement();
                    case "close": connectionClosed = true; connectionCloseCount++; return null;
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (instance, method, args) -> {
                switch (method.getName()) {
                    case "setObject": assertEquals(values.size() + 1, args[0]); values.add(args[1]); return null;
                    case "executeQuery":
                        if (executeFailure != null) { throw executeFailure; }
                        return result();
                    case "close": statementClosed = true; return null;
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        ResultSet result() {
            return proxy(ResultSet.class, (instance, method, args) -> {
                switch (method.getName()) {
                    case "next": return ++position < rows.size();
                    case "getString":
                    case "getInt":
                        if (readFailure != null) { throw readFailure; }
                        return rows.get(position).get((String) args[0]);
                    case "close": resultClosed = true; return null;
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
