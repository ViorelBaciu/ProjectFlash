package net.xqhs.flash.core.testVio;

import test.simplePingPong.Boot;

public class ArgsWrapper extends Boot {
    private String args;
    public ArgsWrapper(String args){
        this.args = args;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }
}
