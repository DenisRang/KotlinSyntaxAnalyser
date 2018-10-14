package examples;


import antlr.KotlinLexer;
import antlr.KotlinParser;
import antlr.KotlinParserBaseListener;
import antlr.KotlinParserBaseVisitor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.util.*;

public class Main {

    private static String toJson(ParseTree tree) {
        Gson prettyPrint = new GsonBuilder().setPrettyPrinting().create();
        return prettyPrint.toJson(toMap(tree));
    }

    private static Map<String, Object> toMap(ParseTree tree) {
        Map<String, Object> map = new LinkedHashMap<>();
        traverse(tree, map);
        return map;
    }

    private static void traverse(ParseTree tree, Map<String, Object> map) {
        if (tree instanceof TerminalNodeImpl) {
            Token token = ((TerminalNodeImpl) tree).getSymbol();

            map.put("type", KotlinParser.VOCABULARY.getSymbolicName(token.getType()));
            map.put("text", token.getText());
        } else {
            List<Map<String, Object>> children = new ArrayList<>();
            String name = tree.getClass().getSimpleName().replaceAll("Context$", "");
            map.put(Character.toLowerCase(name.charAt(0)) + name.substring(1), children);

            for (int i = 0; i < tree.getChildCount(); i++) {
                Map<String, Object> nested = new LinkedHashMap<>();
                children.add(nested);
                traverse(tree.getChild(i), nested);
            }
        }
    }

    public static void main(String[] args) {
        try {
            KotlinLexer lexer = new KotlinLexer(CharStreams.fromFileName("test.kt"));
            KotlinParser parser = new KotlinParser(new CommonTokenStream(lexer));
            System.out.println(toJson(parser.kotlinFile()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


