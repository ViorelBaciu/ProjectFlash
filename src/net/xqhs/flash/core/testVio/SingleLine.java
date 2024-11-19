package net.xqhs.flash.core.testVio;


import test.simplePingPong.Boot;

import java.util.Arrays;

public class SingleLine extends test.simplePingPong.Boot {

    public static String extractAgent(String fullArgs, String agentId){
        String keyword = "-agent " + agentId;
        return extractTargetArgs(fullArgs,keyword);
    }

    public static String extractTargetArgs(String fullArgs, String startKeyword){
        return Arrays.stream(fullArgs.split(" "))
                .dropWhile(part -> !part.equals(startKeyword))
                .skip(1)
                .takeWhile(part-> !part.startsWith("-agent"))
                .reduce((a, b) -> a + " " + b)
                .orElseThrow(() -> new IllegalArgumentException("Keyword not found in arguments"));
    }


}

