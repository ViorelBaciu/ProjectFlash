package net.xqhs.flash.core;


import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.util.logging.UnitComponentExt;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DeploymentUtils {

    public static class PublicCtxtTriple extends DeploymentConfigurationProxy.PublicCtxtTriple {
        public PublicCtxtTriple(String cat, MultiTreeMap categoryTree, MultiTreeMap elTree) {
            super(cat, categoryTree, elTree);
        }
    }

    public static void publicReadCLIArgs(DeploymentConfiguration config, Iterator<String> args, PublicCtxtTriple baseContext, MultiTreeMap rootTree, List<String> autoCreated, Map<String, String> name_ids, UnitComponentExt log) {
        DeploymentConfigurationProxy.publicReadCLIArgs(config, args, baseContext, rootTree, autoCreated, name_ids, log);
    }
}