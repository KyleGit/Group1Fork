package com.napier.sem;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the report queries against the unmodified, seeded db/world.sql data.
 * Run with mvn -Pintegration verify after starting the database service.
 */
class ReportsIT {
    private static App app;

    @BeforeAll
    static void connectToSeededWorldDatabase() {
        app = new App();
        app.connect(System.getProperty("db.address", "localhost:33060"), 1000);
    }

    @AfterAll
    static void disconnect() {
        if (app != null) {
            app.disconnect();
        }
    }

    @Test
    void allCountriesIncludesTerritoriesWithoutCapitalsAndMapsEveryColumn() {
        List<Country> countries = app.getAllCountries();
        assertEquals(239, countries.size(), "An inner join would incorrectly omit seven countries");
        assertCountriesDescending(countries);
        Country china = countries.get(0);
        assertAll(
                () -> assertEquals("CHN", china.code),
                () -> assertEquals("China", china.name),
                () -> assertEquals("Asia", china.continent),
                () -> assertEquals("Eastern Asia", china.region),
                () -> assertEquals(1_277_558_000, china.population),
                () -> assertEquals("Peking", china.capital));
        Country antarctica = countries.stream().filter(c -> "ATA".equals(c.code)).findFirst().orElseThrow();
        assertEquals("Antarctica", antarctica.name);
        assertEquals(0, antarctica.population);
        assertNull(antarctica.capital);
    }

    @Test
    void countriesFilterByContinentAndRegionInPopulationOrder() {
        List<Country> europe = app.getCountriesByContinent("Europe");
        assertEquals(46, europe.size());
        assertTrue(europe.stream().allMatch(c -> "Europe".equals(c.continent)));
        assertEquals("Russian Federation", europe.get(0).name);
        assertCountriesDescending(europe);

        List<Country> caribbean = app.getCountriesByRegion("Caribbean");
        assertEquals(24, caribbean.size());
        assertTrue(caribbean.stream().allMatch(c -> "Caribbean".equals(c.region)));
        assertEquals("Cuba", caribbean.get(0).name);
        assertCountriesDescending(caribbean);
    }

    @Test
    void topCountryQueriesApplyTheLimitAfterFiltering() {
        assertEquals(List.of("China", "India", "United States"), countryNames(app.getTopCountries(3)));
        assertEquals(List.of("Russian Federation", "Germany", "United Kingdom"),
                countryNames(app.getTopCountriesByContinent("Europe", 3)));
        assertEquals(List.of("Cuba", "Dominican Republic", "Haiti"),
                countryNames(app.getTopCountriesByRegion("Caribbean", 3)));
        assertEquals(24, app.getTopCountriesByRegion("Caribbean", 100).size(),
                "A limit larger than the result set must return every matching country");
    }

    @Test
    void allCitiesMapsTheCountryNameAndDistrictAndSortsByPopulation() {
        List<City> cities = app.getAllCities();
        assertEquals(4079, cities.size());
        assertCitiesDescending(cities);
        City mumbai = cities.get(0);
        assertAll(
                () -> assertEquals("Mumbai (Bombay)", mumbai.name),
                () -> assertEquals("India", mumbai.country_name),
                () -> assertEquals("Maharashtra", mumbai.district),
                () -> assertEquals(10_500_000, mumbai.population));
    }

    @Test
    void citiesFilterByContinentAndRegion() {
        List<City> africa = app.getCitiesByContinent("Africa");
        Set<String> africanCountries = app.getCountriesByContinent("Africa").stream()
                .map(c -> c.name).collect(Collectors.toSet());
        assertEquals(366, africa.size());
        assertTrue(africa.stream().allMatch(c -> africanCountries.contains(c.country_name)));
        assertEquals("Cairo", africa.get(0).name);
        assertCitiesDescending(africa);

        List<City> westernEurope = app.getCitiesByRegion("Western Europe");
        Set<String> westernEuropeanCountries = app.getCountriesByRegion("Western Europe").stream()
                .map(c -> c.name).collect(Collectors.toSet());
        assertFalse(westernEurope.isEmpty());
        assertTrue(westernEurope.stream().allMatch(c -> westernEuropeanCountries.contains(c.country_name)));
        assertEquals("Berlin", westernEurope.get(0).name);
        assertCitiesDescending(westernEurope);
    }

    @Test
    void citiesFilterByCountryAndDistrict() {
        List<City> afghanistan = app.getCitiesByCountry("Afghanistan");
        assertEquals(List.of("Kabul", "Qandahar", "Herat", "Mazar-e-Sharif"), cityNames(afghanistan));
        assertTrue(afghanistan.stream().allMatch(c -> "Afghanistan".equals(c.country_name)));
        assertCitiesDescending(afghanistan);

        List<City> district = app.getCitiesByDistrict("Zuid-Holland");
        assertEquals(List.of("Rotterdam", "Haag", "Dordrecht", "Leiden", "Zoetermeer", "Delft"),
                cityNames(district));
        assertTrue(district.stream().allMatch(c -> "Zuid-Holland".equals(c.district)));
        assertCitiesDescending(district);
    }

    @Test
    void topCityQueriesFilterBeforeApplyingTheLimit() {
        assertEquals(List.of("Mumbai (Bombay)", "Seoul", "São Paulo"), cityNames(app.getTopCities(3)));
        assertEquals(List.of("São Paulo", "Lima", "Santafé de Bogotá"),
                cityNames(app.getTopCitiesByContinent("South America", 3)));
        assertEquals(List.of("Berlin", "Paris", "Hamburg"),
                cityNames(app.getTopCitiesByRegion("Western Europe", 3)));
        assertEquals(List.of("Kabul", "Qandahar"), cityNames(app.getTopCitiesByCountry("Afghanistan", 2)));
        assertEquals(List.of("Rotterdam", "Haag"), cityNames(app.getTopCitiesByDistrict("Zuid-Holland", 2)));
        assertEquals(4, app.getTopCitiesByCountry("Afghanistan", 100).size());
    }

    @Test
    void allCapitalCitiesIncludesOnlyEachCountrysDesignatedCapital() {
        List<City> capitals = app.getAllCapitalCities();
        assertEquals(232, capitals.size());
        assertCitiesDescending(capitals);
        assertEquals("Seoul", capitals.get(0).name);
        assertEquals("South Korea", capitals.get(0).country_name);
        assertEquals(9_981_619, capitals.get(0).population);
        assertFalse(cityNames(capitals).contains("Mumbai (Bombay)"));

        // A capital is linked through country.Capital, not merely through its CountryCode.
        for (Country country : app.getAllCountries()) {
            List<City> matching = capitals.stream().filter(c -> country.name.equals(c.country_name))
                    .collect(Collectors.toList());
            if (country.capital == null) {
                assertTrue(matching.isEmpty(), country.name);
            } else {
                assertEquals(1, matching.size(), country.name);
                assertEquals(country.capital, matching.get(0).name, country.name);
            }
        }
    }

    @Test
    void capitalCitiesFilterByContinentAndRegion() {
        List<City> africa = app.getCapCitiesByContinent("Africa");
        assertEquals(57, africa.size());
        assertEquals(List.of("Cairo", "Kinshasa", "Addis Abeba"), cityNames(africa.subList(0, 3)));
        Set<String> africanCountries = app.getCountriesByContinent("Africa").stream()
                .map(c -> c.name).collect(Collectors.toSet());
        assertTrue(africa.stream().allMatch(c -> africanCountries.contains(c.country_name)));
        assertCitiesDescending(africa);

        List<City> britishIslands = app.getCapCitiesByRegion("British Islands");
        assertEquals(List.of("London", "Dublin"), cityNames(britishIslands));
        assertCitiesDescending(britishIslands);
    }

    @Test
    void topCapitalQueriesExcludeLargerNonCapitalCities() {
        assertEquals(List.of("Seoul", "Jakarta", "Ciudad de México"), cityNames(app.getTopCapCities(3)),
                "The world top-capital query must not return Mumbai, a non-capital city");
        assertEquals(List.of("Moscow", "London", "Berlin"),
                cityNames(app.getTopCapCitiesByContinent("Europe", 3)));
        assertEquals(List.of("London"), cityNames(app.getTopCapCitiesByRegion("British Islands", 1)));
        assertEquals(2, app.getTopCapCitiesByRegion("British Islands", 100).size());
    }

    @Test
    void populationTiesHaveStableOrderAtTheTopNBoundary() {
        // These countries both have 38,000 people; Code breaks the tie.
        List<Country> countries = app.getCountriesByRegion("Caribbean");
        assertEquals(List.of("Cayman Islands", "Saint Kitts and Nevis"),
                countryNames(countries.stream().filter(c -> c.population == 38_000)
                        .collect(Collectors.toList())));
        List<Country> topCountries = app.getTopCountriesByRegion("Caribbean", 19);
        assertEquals(19, topCountries.size());
        assertEquals("Cayman Islands", topCountries.get(18).name);

        // Lida (ID 3533) precedes Soligorsk (ID 3534), both population 101,000.
        List<City> cities = app.getCitiesByCountry("Belarus");
        assertEquals(List.of("Lida", "Soligorsk"),
                cityNames(cities.stream().filter(c -> c.population == 101_000)
                        .collect(Collectors.toList())));
        List<City> topCities = app.getTopCitiesByCountry("Belarus", 14);
        assertEquals(14, topCities.size());
        assertEquals("Lida", topCities.get(13).name);
    }

    @Test
    void mainRunsEveryDemonstrationReportAgainstTheConfiguredDatabase() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            assertDoesNotThrow(() -> App.main(new String[]{
                    System.getProperty("db.address", "localhost:33060"), "0"}));
        } finally {
            System.setOut(original);
        }

        String output = buffer.toString(StandardCharsets.UTF_8);
        List<String> lines = output.lines().map(line -> line.strip().replaceAll("\\s+", " "))
                .collect(Collectors.toList());
        assertAll(
                () -> assertEquals(6, lines.stream()
                        .filter("Code Name Continent Region Population Capital"::equals).count()),
                () -> assertEquals(10, lines.stream()
                        .filter("Name Country District Population"::equals).count()),
                () -> assertEquals(6, lines.stream()
                        .filter("Name Country Population"::equals).count()),
                () -> assertTrue(lines.contains("CHN China Asia Eastern Asia 1277558000 Peking")),
                () -> assertTrue(lines.contains("Rotterdam Netherlands Zuid-Holland 593321")),
                () -> assertTrue(lines.contains("Seoul South Korea 9981619")),
                () -> assertTrue(lines.contains("Jakarta Indonesia 9604900")),
                () -> assertFalse(output.toLowerCase(java.util.Locale.ROOT).matches("(?s).*\\b(failed|failure|null)\\b.*")));
    }

    @Test
    void unknownFiltersReturnEmptyResultsForEveryReportVariant() {
        String unknown = "No such place";
        assertAll(
                () -> assertTrue(app.getCountriesByContinent(unknown).isEmpty()),
                () -> assertTrue(app.getCountriesByRegion(unknown).isEmpty()),
                () -> assertTrue(app.getTopCountriesByContinent(unknown, 3).isEmpty()),
                () -> assertTrue(app.getTopCountriesByRegion(unknown, 3).isEmpty()),
                () -> assertTrue(app.getCitiesByContinent(unknown).isEmpty()),
                () -> assertTrue(app.getCitiesByRegion(unknown).isEmpty()),
                () -> assertTrue(app.getCitiesByCountry(unknown).isEmpty()),
                () -> assertTrue(app.getCitiesByDistrict(unknown).isEmpty()),
                () -> assertTrue(app.getTopCitiesByContinent(unknown, 3).isEmpty()),
                () -> assertTrue(app.getTopCitiesByRegion(unknown, 3).isEmpty()),
                () -> assertTrue(app.getTopCitiesByCountry(unknown, 3).isEmpty()),
                () -> assertTrue(app.getTopCitiesByDistrict(unknown, 3).isEmpty()),
                () -> assertTrue(app.getCapCitiesByContinent(unknown).isEmpty()),
                () -> assertTrue(app.getCapCitiesByRegion(unknown).isEmpty()),
                () -> assertTrue(app.getTopCapCitiesByContinent(unknown, 3).isEmpty()),
                () -> assertTrue(app.getTopCapCitiesByRegion(unknown, 3).isEmpty()));
    }

    @Test
    void quotedFiltersAreTreatedAsDataRatherThanSql() {
        String quoted = "' OR '1'='1";
        assertAll(
                () -> assertTrue(app.getCountriesByContinent(quoted).isEmpty()),
                () -> assertTrue(app.getCountriesByRegion(quoted).isEmpty()),
                () -> assertTrue(app.getTopCountriesByContinent(quoted, 3).isEmpty()),
                () -> assertTrue(app.getTopCountriesByRegion(quoted, 3).isEmpty()),
                () -> assertTrue(app.getCitiesByContinent(quoted).isEmpty()),
                () -> assertTrue(app.getCitiesByRegion(quoted).isEmpty()),
                () -> assertTrue(app.getCitiesByCountry(quoted).isEmpty()),
                () -> assertTrue(app.getCitiesByDistrict(quoted).isEmpty()),
                () -> assertTrue(app.getTopCitiesByContinent(quoted, 3).isEmpty()),
                () -> assertTrue(app.getTopCitiesByRegion(quoted, 3).isEmpty()),
                () -> assertTrue(app.getTopCitiesByCountry(quoted, 3).isEmpty()),
                () -> assertTrue(app.getTopCitiesByDistrict(quoted, 3).isEmpty()),
                () -> assertTrue(app.getCapCitiesByContinent(quoted).isEmpty()),
                () -> assertTrue(app.getCapCitiesByRegion(quoted).isEmpty()),
                () -> assertTrue(app.getTopCapCitiesByContinent(quoted, 3).isEmpty()),
                () -> assertTrue(app.getTopCapCitiesByRegion(quoted, 3).isEmpty()));
    }

    private static List<String> countryNames(List<Country> countries) {
        return countries.stream().map(c -> c.name).collect(Collectors.toList());
    }

    private static List<String> cityNames(List<City> cities) {
        return cities.stream().map(c -> c.name).collect(Collectors.toList());
    }

    private static void assertCountriesDescending(List<Country> countries) {
        for (int i = 1; i < countries.size(); i++) {
            assertTrue(countries.get(i - 1).population >= countries.get(i).population,
                    "Countries out of population order at index " + i);
        }
    }

    private static void assertCitiesDescending(List<City> cities) {
        for (int i = 1; i < cities.size(); i++) {
            assertTrue(cities.get(i - 1).population >= cities.get(i).population,
                    "Cities out of population order at index " + i);
        }
    }
}
