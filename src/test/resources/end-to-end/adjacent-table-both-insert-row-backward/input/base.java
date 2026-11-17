/*
   Copyright 2015 Barend Garvelink

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package nl.garvelink.iban;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Ensures that the {@link nl.garvelink.iban.IBAN} class accepts IBAN numbers from every participating country (...known at the time the test was last updated).
 */
@RunWith(Parameterized.class)
public class CountryCodesParameterizedTest {

    private final String plain;
    private final String pretty;

    @SuppressWarnings("unused")
    public CountryCodesParameterizedTest(String testName, String sepa, String plain, String bankIdentifier, String branchIdentfier, String pretty) {
        this.plain = plain;
        this.pretty = pretty;
    }

    /**
     * List of valid international IBAN's.
     * <p>References:</p>
     * <dl>
     *     <dt>ECBS</dt>
     *     <dd>http://www.ecbs.org/iban.htm</dd>
     *     <dt>SWIFT</dt>
     *     <dd>http://www.swift.com/dsp/resources/documents/IBAN_Registry.pdf (release 58)</dd>
     *     <dt>Nordea</dt>
     *     <dd>http://www.nordea.com/Our+services/International+products+and+services/Cash+Management/IBAN+countries/908462.html (Oct 20, 2013)</dd>
     * </dl>
     * <p>The Nordea link was obtained through Wikipedia.</p>
     * <p>Political note: the SWIFT documentation uses "State of Palestine", as does this source file. The initial
     * version of this source file was based on Wikipedia and used "Palistinian Territories" [sic], which has a typo and
     * is wrong either way. The author chose to follow the name used by SWIFT, since they are the governing body of the
     * IBAN standard. No statement of political preference either way is implied, or should be inferred.</p>
     */
    static final List<String[]> PARAMETERS = Arrays.asList(
            str( "Hungary",                  "true" , "HU42117730161111101800000000",    "117"     , "7301"    , "HU42 1177 3016 1111 1018 0000 0000" ),     //SWIFT, ECBS, Nordea
            str( "Iceland",                  "true" , "IS140159260076545510730339",      "0159"    , null      , "IS14 0159 2600 7654 5510 7303 39" ),       //SWIFT, ECBS, Nordea
            str( "Iran",                     "false", "IR580540105180021273113007",      null      , null      , "IR58 0540 1051 8002 1273 1130 07"),        //Nordea
            str( "Ireland",                  "true" , "IE29AIBK93115212345678",          "AIBK"    , "931152"  , "IE29 AIBK 9311 5212 3456 78" ),            //SWIFT, ECBS, Nordea
            str( "Israel",                   "false", "IL620108000000099999999",         "010"     , "800"     , "IL62 0108 0000 0009 9999 999" ),           //SWIFT, ECBS, Nordea
            str( "Italy",                    "true" , "IT60X0542811101000000123456",     "05428"   , "11101"   , "IT60 X054 2811 1010 0000 0123 456" ),      //SWIFT, ECBS, Nordea
            str( "Ivory Coast",              "false", "CI05A00060174100178530011852",    null      , null      , "CI05 A000 6017 4100 1785 3001 1852"),      //Nordea
            str( "Jordan",                   "false", "JO94CBJO0010000000000131000302",  "CBJO"    , null      , "JO94 CBJO 0010 0000 0000 0131 0003 02"),   //SWIFT
            str( "Virgin Islands, British",  "false", "VG96VPVG0000012345678901",        "VPVG"    , null      , "VG96 VPVG 0000 0123 4567 8901")            //SWIFT, Nordea
        );

    @Parameterized.Parameters(name = " {0} ")
    public static List<String[]> parameters() {
        return PARAMETERS;
    }

    @Test
    public void getLengthForCountryCodeShouldReturnCorrectValue() {
        assertEquals(plain.length(), CountryCodes.getLengthForCountryCode(plain.substring(0, 2)));
    }

    @Test
    public void isKnownCountryCodeShouldReturnTrue() {
        assertTrue(CountryCodes.isKnownCountryCode(plain.substring(0, 2)));
    }

    /**
     * Used by the {@code xxxTestShouldBeExhaustive()} methods to ensure that there are no untested country codes
     * defined in the {@code CountryCodes} class. The inverse, a country code that exists in the test but not in
     * the application code does not have to be separately verified.
     * @see #prepareTestShouldBeExhaustive()
     * @see #updateTestShouldBeExhaustive()
     * @see #finishTestShouldBeExhaustive()
     */
    private static final Set<String> allCountryCodes = Collections.synchronizedSet(new HashSet<String>(PARAMETERS.size()));

    @BeforeClass
    public static void prepareTestShouldBeExhaustive() {
        allCountryCodes.addAll(CountryCodes.getKnownCountryCodes());
    }

    @Test
    public void updateTestShouldBeExhaustive() {
        allCountryCodes.remove(plain.substring(0, 2));
    }

    @AfterClass
    public static void finishTestShouldBeExhaustive() {
        assertTrue("There are entries in CountryCodes.java that are not covered in test: " + allCountryCodes,
                allCountryCodes.isEmpty());
    }

    /**
     * Helps put the parameter data into readable formatting.
     */
    private static final String[] str(String... strs) {
        return strs;
    }
}
