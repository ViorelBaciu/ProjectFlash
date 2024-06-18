package net.xqhs.flash.core;

import net.xqhs.flash.core.util.MultiTreeMap;

import java.util.Arrays;
import java.util.Scanner;

public class CommandLineInterface {

    public static void main(String[] args) throws Exception {
        // Initialize DynamicNode with node configuration
        MultiTreeMap nodeConfig = new MultiTreeMap(); // Load or initialize your node configuration
        DynamicNode node = new DynamicNode(nodeConfig);
        node.start(); // Start the node

        Scanner scanner = new Scanner(System.in);
        System.out.println("Command-Line Interface started. Type 'help' for a list of commands.");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            String[] cliArgs = input.split(" ");

            if (cliArgs[0].equalsIgnoreCase("exit")) {
                break;
            } else if (cliArgs[0].equalsIgnoreCase("addAgent")) {
                node.dynamicAddAgent(Arrays.copyOfRange(cliArgs, 1, cliArgs.length));
            } else if (cliArgs[0].equalsIgnoreCase("addShard")) {
                node.dynamicAddShard(Arrays.copyOfRange(cliArgs, 1, cliArgs.length));
            } else if (cliArgs[0].equalsIgnoreCase("list")) {
                if (cliArgs[1].equalsIgnoreCase("agents")) {
                    node.listAgents();
                } else {
                    System.out.println("Unknown category to list. Supported categories: agents");
                }
            } else if (cliArgs[0].equalsIgnoreCase("help")) {
                System.out.println("Available commands:");
                System.out.println("  addAgent <arguments> - Add an agent");
                System.out.println("  addShard <arguments> - Add a shard");
                System.out.println("  list agents          - List all added agents");
                System.out.println("  exit                 - Exit the interface");
            } else {
                System.out.println("Unknown command. Type 'help' for a list of available commands.");
            }
        }

        scanner.close();
        node.stop();
        System.out.println("Command-Line Interface stopped.");
    }
}