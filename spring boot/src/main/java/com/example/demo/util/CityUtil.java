package com.example.demo.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CityUtil {
    private static final Set<String> KNOWN_CITIES = new HashSet<>();

    static {
        // North India
        add("Delhi","New Delhi","Jammu","Udhampur","Katra","Shimla","Manali","Solan",
            "Amritsar","Ludhiana","Jalandhar","Patiala","Bathinda",
            "Chandigarh","Panchkula","Mohali",
            "Panipat","Sonipat","Rohtak","Karnal","Ambala","Hisar","Sirsa","Rewari","Kurukshetra",
            "Jaipur","Jodhpur","Udaipur","Ajmer","Bikaner","Kota","Alwar","Bharatpur","Sikar","Tonk",
            "Lucknow","Kanpur","Varanasi","Allahabad","Prayagraj","Gorakhpur","Ayodhya","Faizabad",
            "Bareilly","Moradabad","Meerut","Saharanpur","Muzaffarnagar","Bulandshahr","Mathura",
            "Agra","Aligarh","Sitapur","Ballia","Mau","Firozabad","Hathras");

        // West India
        add("Mumbai","Navi Mumbai","Thane","Kalyan","Vasai","Pune","Nagpur","Nashik",
            "Aurangabad","Kolhapur","Sangli","Satara","Jalgaon","Dhule","Ahmednagar",
            "Latur","Beed","Osmanabad","Akola","Amravati","Chandrapur","Wardha","Yavatmal","Solapur",
            "Ahmedabad","Surat","Vadodara","Rajkot","Gandhinagar","Jamnagar","Bhavnagar",
            "Junagadh","Porbandar","Morbi","Mehsana","Anand","Nadiad","Palanpur",
            "Margao","Panaji","Vasco da Gama");

        // South India
        add("Bengaluru","Bangalore","Mysuru","Mangalore","Udupi","Hubli","Dharwad",
            "Belgaum","Davanagere","Bellary","Tumkur",
            "Hyderabad","Secunderabad","Warangal","Nizamabad","Karimnagar","Khammam",
            "Vijayawada","Visakhapatnam","Nellore","Tirupati","Guntur","Kurnool","Anantapur",
            "Kadapa","Ongole","Vizianagaram",
            "Chennai","Coimbatore","Madurai","Salem","Erode","Tiruppur","Thanjavur","Tirunelveli",
            "Tuticorin","Vellore","Kanchipuram","Cuddalore",
            "Kozhikode","Thrissur","Kollam","Alappuzha","Palakkad","Malappuram","Kannur","Kasaragod");

        // East India
        add("Kolkata","Howrah","Durgapur","Asansol","Siliguri","Malda","Jalpaiguri","Darjeeling","Kharagpur",
            "Patna","Muzaffarpur","Gaya","Darbhanga","Bhagalpur","Purnia","Katihar","Samastipur","Chhapra","Siwan",
            "Ranchi","Jamshedpur","Bokaro","Hazaribagh","Dhanbad","Daltonganj",
            "Bhubaneswar","Cuttack","Rourkela","Sambalpur","Puri","Balasore","Berhampur");

        // North-East India
        add("Guwahati","Dibrugarh","Silchar","Tezpur",
            "Shillong","Tura","Nongpoh",
            "Aizawl","Lunglei",
            "Kohima","Dimapur","Mokokchung",
            "Imphal","Thoubal",
            "Agartala","Udaipur",
            "Itanagar","Naharlagun",
            "Gangtok","Namchi");
    }

    private CityUtil() {}

    private static void add(String... names) {
        for (String n : names) {
            KNOWN_CITIES.add(n);
        }
    }

    public static boolean isKnownCity(String name) {
        if (name == null) return false;
        return KNOWN_CITIES.contains(name) || KNOWN_CITIES.contains(toTitle(name));
    }

    public static Set<String> getAllCities() {
        return new HashSet<>(KNOWN_CITIES);
    }

    public static String toTitle(String str) {
        if (str == null || str.isBlank()) return str;
        StringBuilder sb = new StringBuilder();
        String[] parts = str.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }
}


