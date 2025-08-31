package br.ufmg.utils;

import java.util.Base64;

public class Base64Parser {
    private static String removeTrailingEquals(String encodedUrl){
        int lastTrailingEqual = encodedUrl.length();
        for(int i = encodedUrl.length() - 1; i >= Math.max(encodedUrl.length() - 4, 0); i--){
            if(encodedUrl.charAt(i) != '='){
                break;
            }
            lastTrailingEqual = i;  
        }
        encodedUrl = encodedUrl.substring(0, lastTrailingEqual);
        return encodedUrl;
    }

    private static String restoreTrailingEquals(String encodedUrl){
        int paddingAmount = 4 - (encodedUrl.length() % 4);
        if(paddingAmount != 4){
            String padding = "=".repeat(paddingAmount);
            encodedUrl += padding;
        }
        return encodedUrl;
    }

    public static String encode(String originalUrl){
        Base64.Encoder urlEncoder = Base64.getUrlEncoder();

        String encodedUrl = urlEncoder.encodeToString(originalUrl.getBytes());
        encodedUrl = removeTrailingEquals(encodedUrl);

        return encodedUrl;
    }

    public static String decode(String encodedUrl){
        encodedUrl = restoreTrailingEquals(encodedUrl);

        Base64.Decoder urlDecoder = Base64.getUrlDecoder();

        byte[] decodedBytes = urlDecoder.decode(encodedUrl);
        String decodedString = new String(decodedBytes);

        return decodedString;
    }
}