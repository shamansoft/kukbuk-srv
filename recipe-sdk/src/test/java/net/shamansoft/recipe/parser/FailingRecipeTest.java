package net.shamansoft.recipe.parser;

import net.shamansoft.recipe.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for recipes that were failing in production.
 * These tests verify that the parser is lenient enough to handle real-world YAML data.
 */
class FailingRecipeTest {

    private YamlRecipeParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlRecipeParser();
    }

    @Test
    void shouldParseRussianHachapuriRecipe() throws RecipeParseException {
        String yaml = """
                is_recipe: true
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Хачапури с сулугуни на кефире"
                  source: "https://eda.rambler.ru/recepty/vypechka-deserty/hachapuri-s-suluguni-na-kefire-42267"
                  author: "Aleksey Khisamutdinov"
                  language: "en"
                  date_created: "2014-02-09"
                  category:
                  - "Выпечка и десерты"
                  tags: []
                  servings: 5
                  prep_time: "0d"
                  cook_time: "1h30m"
                  total_time: "1h30m"
                  difficulty: "medium"
                  cover_image: null
                description: "Хачапури с сулугуни на кефире. Проверенный пошаговый рецепт для приготовления в домашних условиях. С описанием, фото или видео, советами и лайфхаками от экспертов, оценкой и отзывами пользователей. Умный поиск рецептов на сайте и гастрономические идеи на Eda.ru"
                ingredients:
                - item: "Кефир"
                  amount: "250"
                  unit: "мл"
                  notes: null
                  optional: false
                  substitutions: null
                  component: "main"
                - item: "Пшеничная мука"
                  amount: "500"
                  unit: "г"
                  notes: null
                  optional: false
                  substitutions: null
                  component: "main"
                - item: "Соль"
                  amount: null
                  unit: null
                  notes: "щепотка"
                  optional: false
                  substitutions: null
                  component: "main"
                - item: "Разрыхлитель"
                  amount: null
                  unit: null
                  notes: "щепотка"
                  optional: false
                  substitutions: null
                  component: "main"
                - item: "Растительное масло"
                  amount: "2"
                  unit: "столовые ложки"
                  notes: null
                  optional: false
                  substitutions: null
                  component: "main"
                - item: "Куриное яйцо"
                  amount: "1"
                  unit: "штука"
                  notes: null
                  optional: false
                  substitutions: null
                  component: "main"
                - item: "Сыр сулугуни"
                  amount: "300"
                  unit: "г"
                  notes: null
                  optional: false
                  substitutions: null
                  component: "main"
                equipment:
                - "духовка"
                - "противень"
                - "сито"
                instructions:
                - step: 1
                  description: "Тесто: мука (половина) + кефир + соль + разрыхлитель. Замесить ложкой, затем месить руками, поливая руки маслом, чтобы не прилипало тесто и добавляя муку. В результате должно получиться 3 шарика диаметром примерно 10 см. Поставить в холодильник на час."
                  time: "1h"
                  temperature: null
                  media: null
                - step: 2
                  description: "Начинка: сыр натереть на крупной терке, добавить яйцо (на двойную порцию так же 1 яйца хватит), размять, сформировать 3 шарика."
                  time: null
                  temperature: null
                  media: null
                - step: 3
                  description: "Взять шарик теста, чуть приплюснуть его, положить на него шарик сыра и вдавить его в тесто. Края теста защипать над сырным шаром. Перевернуть получившийся шар и раскатать в лепешку диаметров примерно 30 см."
                  time: null
                  temperature: null
                  media: null
                - step: 4
                  description: "Положить на противень, предварительно слегка посыпанный мукой, защипанной стороной вниз, сверку лепешку в центре чуть надорвать до начинки."
                  time: null
                  temperature: null
                  media: null
                - step: 5
                  description: "Выпекать в духовке при температуре 200 градусов около 10 минут."
                  time: "10m"
                  temperature: "200°C"
                  media: null
                - step: 6
                  description: "Переложить на блюдо, сверку смазать сливочным маслом."
                  time: null
                  temperature: null
                  media: null
                nutrition:
                  serving_size: "порция"
                  calories: 625
                  protein: 24.0
                  carbohydrates: null
                  fat: null
                  fiber: null
                  sugar: 77.0
                  sodium: null
                  notes: null
                notes: ""
                storage:
                  refrigerator: null
                  freezer: null
                  room_temperature: null
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.isRecipe()).isTrue();
        assertThat(recipe.metadata().title()).isEqualTo("Хачапури с сулугуни на кефире");
        assertThat(recipe.ingredients()).hasSize(7);
        assertThat(recipe.instructions()).hasSize(6);
    }

    @Test
    void shouldHandleUnknownProperties() throws RecipeParseException {
        String yaml = """
                is_recipe: true
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                unknown_field: "This should be ignored"
                metadata:
                  title: "Test Recipe"
                  source: "https://example.com"
                  servings: 4
                  unknown_metadata_field: "Also ignored"
                ingredients:
                  - item: "flour"
                    amount: "2"
                    unit: "cups"
                    unknown_ingredient_field: "Ignored too"
                instructions:
                  - step: 1
                    description: "Mix"
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.metadata().title()).isEqualTo("Test Recipe");
    }

    @Test
    void shouldHandleExplicitNulls() throws RecipeParseException {
        String yaml = """
                is_recipe: true
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test Recipe"
                  source: null
                  author: null
                  servings: 4
                  cover_image: null
                ingredients:
                  - item: "flour"
                    amount: null
                    unit: null
                    notes: null
                    substitutions: null
                instructions:
                  - step: 1
                    description: "Mix"
                    time: null
                    temperature: null
                    media: null
                nutrition: null
                storage: null
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.metadata().title()).isEqualTo("Test Recipe");
        assertThat(recipe.metadata().source()).isNull();
        assertThat(recipe.nutrition()).isNull();
        assertThat(recipe.storage()).isNull();
    }

    @Test
    void shouldHandleEmptyArrays() throws RecipeParseException {
        String yaml = """
                is_recipe: true
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test Recipe"
                  servings: 4
                  category: []
                  tags: []
                ingredients:
                  - item: "flour"
                    amount: "2"
                    unit: "cups"
                    substitutions: []
                equipment: []
                instructions:
                  - step: 1
                    description: "Mix"
                    media: []
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.metadata().category()).isEmpty();
        assertThat(recipe.metadata().tags()).isEmpty();
        assertThat(recipe.equipment()).isEmpty();
    }

    @Test
    void shouldHandleEmptyStrings() throws RecipeParseException {
        String yaml = """
                is_recipe: true
                schema_version: "1.0.0"
                recipe_version: "1.0.0"
                metadata:
                  title: "Test Recipe"
                  source: ""
                  servings: 4
                ingredients:
                  - item: "flour"
                    amount: "2"
                    unit: ""
                    notes: ""
                instructions:
                  - step: 1
                    description: "Mix"
                description: ""
                notes: ""
                """;

        Recipe recipe = parser.parse(yaml);

        assertThat(recipe).isNotNull();
        assertThat(recipe.metadata().title()).isEqualTo("Test Recipe");
        // Empty strings should be treated as empty, not null
        assertThat(recipe.description()).isEqualTo("");
        assertThat(recipe.notes()).isEqualTo("");
    }
}
