package examples; /**
 * An example of using generated files
 * Printing out the parse tree
 */

import antlr.KotlinLexer;
import antlr.KotlinParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.util.Objects;

public class Serializer {

    private static JsonObject resultJson = new JsonObject();

    public static void main(String[] args) throws Exception {
        KotlinLexer lexer = new KotlinLexer(new ANTLRFileStream("test.kt"));
        TokenStream tokens = new CommonTokenStream(lexer);
        KotlinParser parser = new KotlinParser(tokens);
        resultJson.add("root", new JsonObject());
        printTree(parser.kotlinFile(), 0, lexer);
        System.out.println("S");
        //printResultJson();
    }

    private static void printTree(ParseTree tree, int indentation, KotlinLexer lexer) throws Exception {
        //printIndentation(indentation);
        String treeClassName = tree.getClass().getSimpleName();
        if (Objects.equals(treeClassName, "TerminalNodeImpl")) {
            TerminalNodeImpl node = (TerminalNodeImpl) tree;
            printNode(node, lexer);
        } else {
            printRule(tree);
            if (tree.getChildCount() == 0) {
                //printIndentation(indentation);
                System.out.println("  <empty list>");
            }
            for (int i = 0; i < tree.getChildCount(); i++) {
                printTree(tree.getChild(i), indentation + 1, lexer);
            }
        }
    }

    private static void printIndentation(int indentation) throws Exception {
        String output = "";
        for (int i = 0; i < indentation; i++) {
            output += "  ";
        }
        System.out.print(output);
    }

    private static int getNodeTypeNumber(TerminalNodeImpl node) throws Exception {
        return node.getSymbol().getType();
    }

    private static void printNode(TerminalNodeImpl node, KotlinLexer lexer) throws Exception {
        String nodeType = lexer.getVocabulary().getSymbolicName(getNodeTypeNumber(node));
        String nodeText = node.getText();
        if (Objects.equals(nodeType, "NL")) nodeText = "\\n";
        System.out.println("PsiElement" + "(" + nodeType + ")" + "('" + nodeText + "')");
        resultJson.addProperty("PsiElement", "(" + nodeType + ")" + "('" + nodeText + "')");
    }

    private static void printRule(ParseTree tree) throws Exception {
        char[] ruleName = tree.getClass().getSimpleName().replaceFirst("Context", "").toCharArray();
        ruleName[0] = Character.toLowerCase(ruleName[0]);
        System.out.println(ruleName);
       // resultJson.add(new String(ruleName), resultJson.get);
    }

    private static void printResultJson(JsonObject jsonObject){

    }

}
