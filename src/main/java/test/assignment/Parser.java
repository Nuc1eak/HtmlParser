package test.assignment;

import com.google.gson.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class Parser {

    private static final int PRODUCTS_PER_PAGE = 100;

    private static final String JSON_URL = "https://api-cloud.aboutyou.de/v1/products?with=attributes%3Akey%28brand" +
            "%7CbrandLogo%7CbrandAlignment%7Cname%7CquantityPerPack%7CplusSize%7CcolorDetail%7CsponsorBadge" +
            "%7CsponsoredType%7CmaternityNursing%7Cexclusive%7Cgenderage%7CspecialSizesProduct%7CmaterialStyle" +
            "%7CsustainabilityIcons%7CassortmentType%7CdROPS%29%2CadvancedAttributes%3Akey" +
            "%28materialCompositionTextile%7Csiblings%29%2Cvariants%2Cvariants.attributes%3Akey%28shopSize" +
            "%7CcategoryShopFilterSizes%7Ccup%7Ccupsize%7CvendorSize%7Clength%7Cdimension3%7CsizeType%7Csort%29" +
            "%2Cimages.attributes%3Alegacy%28false%29%3Akey%28imageNextDetailLevel%7CimageBackground%7CimageFocus" +
            "%7CimageGender%7CimageType%7CimageView%29%2CpriceRange&filters%5Bcategory%5D=20290&sortDir=desc" +
            "&sortScore=category_scores&sortChannel=sponsored_web_default&shopId=605&page=1" +
            "&perPage=" + PRODUCTS_PER_PAGE;

    private static final String FILE_NAME = "result.json";
    private static int amountOfTriggeredHttpRequest = 0;


    public static void main(String[] args) throws IOException {
        // receive raw Json from an HTTP request
        String json = readJson(JSON_URL);

        // parsing all products from the page and collecting them to the products list (deserialization)
        List<Product> products = IntStream.range(0, PRODUCTS_PER_PAGE)
                .mapToObj(i -> parseJson(json, i))
                .collect(Collectors.toList());

        // convert all products to a custom json (serialization)
        String finalJson = convertSrcToJson(products);

        writeJson(FILE_NAME, beautifyJson(finalJson));

        System.out.printf("Amount of triggered HTTP requests = %d\n" +
                "Amount of extracted products = %d\n", amountOfTriggeredHttpRequest, products.size());
    }

    /*
        Parsing one product entity from raw json
     */
    public static Product parseJson(String json, int product_i) {
        Product product = new Product();
        JsonElement jsonElement = JsonParser.parseString(json);
        if (jsonElement.isJsonObject()) {

            JsonObject entity = jsonElement.getAsJsonObject()
                    .getAsJsonArray("entities")
                    .get(product_i).getAsJsonObject();

            product.setId(parseIdFromEntity(entity));
            product.setName(parseNameFromEntity(entity));
            product.setBrand(parseBrandFromEntity(entity));
            product.setPrice(parsePriceFromEntity(entity));
            product.setColor(parseColorsFromEntity(entity));
        }
        return product;
    }

    /*
        Parsing ID from product entity
     */
    public static int parseIdFromEntity(JsonObject entity) {
        return entity.get("id").getAsInt();
    }

    /*
        Parsing name from product entity
     */
    public static String parseNameFromEntity(JsonObject entity) {
        return entity.get("attributes").getAsJsonObject()
                .get("name").getAsJsonObject()
                .get("values").getAsJsonObject()
                .get("label").getAsString();
    }

    /*
        Parsing brand from product entity
     */
    public static String parseBrandFromEntity(JsonObject entity) {
        return entity.get("attributes").getAsJsonObject()
                .get("brand").getAsJsonObject()
                .get("values").getAsJsonObject()
                .get("label").getAsString();
    }

    /*
        Parsing price from product entity
     */
    public static int parsePriceFromEntity(JsonObject entity) {
        return entity.get("priceRange").getAsJsonObject()
                .get("min").getAsJsonObject()
                .get("withTax").getAsInt();
    }

    /*
        Parsing main and extra colors from product entity
     */
    public static List<String> parseColorsFromEntity(JsonObject entity) {
        List<String> colors = new ArrayList<>();

        // parsing main color (the first one)
        JsonArray mainColorsArray = entity.get("attributes").getAsJsonObject()
                .get("colorDetail").getAsJsonObject()
                .get("values").getAsJsonArray();

        String mainColor = parseColors(mainColorsArray);
        colors.add(mainColor);

        // parsing extra colors (if there are any)
        if (!entity.get("advancedAttributes").getAsJsonObject().has("siblings")) {
            return colors;
        }
        JsonArray otherColorArray = entity.get("advancedAttributes").getAsJsonObject()
                .get("siblings").getAsJsonObject()
                .get("values").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("fieldSet").getAsJsonArray()
                .get(0).getAsJsonArray();

        for (int i = 0; i < otherColorArray.size(); i++) {
            if (!otherColorArray.get(i).getAsJsonObject().get("isSoldOut").getAsBoolean()) {
                String additionalColor = parseColors(otherColorArray
                        .get(i).getAsJsonObject()
                        .get("colorDetail").getAsJsonArray());
                colors.add(additionalColor);
            }
        }

        return colors;
    }

    /*
        Parsing and converting an array of colors to a String (e.g. "schwarz/weiß")
     */
    private static String parseColors(JsonArray array) {
        return IntStream.range(0, array.size())
                .mapToObj(i -> array.get(i).getAsJsonObject().get("label").getAsString())
                .collect(Collectors.joining("/"));
    }

    /*
        Making an HTTP request using the URL and reading its source with a BufferedInputStream
     */
    public static String readJson(String urlString) throws IOException {
        amountOfTriggeredHttpRequest++;
        StringBuilder parsedContentFromJson = new StringBuilder();
        URL url = new URL(urlString);
        // Creating an URL connection
        URLConnection uc = url.openConnection();
        // Pretend we're browser
        uc.addRequestProperty("User-agent", "Chrome/4.0.249.0 Safari/532.5");
        uc.connect();
        BufferedInputStream in = new BufferedInputStream(uc.getInputStream());
        int ch;
        // Start reading
        while ((ch = in.read()) != -1) parsedContentFromJson.append((char) ch);
        return parsedContentFromJson.toString();
    }

    /*
        Serializing object to a json
     */
    public static String convertSrcToJson(Object src) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(src);
    }

    /*
        Replacing "\u0027" and "\u0026" symbols with "'" and "&"
     */
    private static String beautifyJson(String uglyJson) {
        return uglyJson.replaceAll("\\\\u0027", "'")
                .replaceAll("\\\\u0026", "&");
    }

    /*
        Writing json result to a file
     */
    public static void writeJson(String fileName, String json) {
        try(FileWriter writer = new FileWriter(new File(fileName))) {
            writer.write(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
