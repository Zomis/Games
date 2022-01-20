<template>
  <v-container fluid>
    <v-row>
      <v-col align-self="start">
        <v-card>
          <div class="board">
            <img v-for="(turnOrder, turnOrderIndex) in view.turnPicker.options" :key="'turn-' + turnOrderIndex"
             :src="`${path}cube_${turnOrder.chosenBy}.png`"
             :style="{ left: '1063px', top: (33 + 30*turnOrderIndex) + 'px' }" :class="{ hidden: turnOrder.chosenBy == null }" />
            <img v-for="(cube, cubeIndex) in actionCubes" :key="'cube-' + cubeIndex"
             :src="`${path}cube_${cube.player}.png`"
             :style="{ left: cube.x + 'px', top: cube.y + 'px' }" :class="{ grayed: !cube.visible }" />
            <!--<img v-for=""
            Artifacts: y 236, x: 207+97*n
            -->
            <div class="ingredient-deck">{{ view.ingredients.deck }}</div>
            <div class="favor-deck">{{ view.favors.deck }}</div>
            <img v-for="(artifact, artifactIndex) in artifacts" :key="'artifact-' + artifactIndex"
             :style="{ left: (207 + 97*artifactIndex) + 'px', top: '236px', width: '92px', height: '142px' }" :src="`${path}artifact_${artifact}.jpg`" />
            <img v-for="(ingredient, ingredientIndex) in view.ingredients.slots" :key="'ingredient-' + ingredientIndex"
             :src="`${path}ingredient_${ingredient}.png`"
             :style="{ left: (658+97*ingredientIndex) + 'px', top: '449px' }" />
            <img v-if="view.sellPotion.heroes[0]" :style="{ left: '8px', top: '392px', width: '93px', height: '144px' }"
             :src="`${path}hero_n${view.sellPotion.heroes[0].id}.jpg`" />
            <img v-if="view.sellPotion.heroes[1]" :style="{ left: '-108px', top: '392px', width: '93px', height: '144px' }"
             :src="`${path}hero_n${view.sellPotion.heroes[1].id}.jpg`" />
          </div>
        </v-card>
      </v-col>
    </v-row>
    <v-row justify="center">
    </v-row>
  </v-container>
</template>

<script>
//import PlayerProfile from "@/components/games/common/PlayerProfile"
//import CardZone from "@/components/games/common/CardZone"
/*    CardZone,
    PlayerProfile,
    HanabiCard*/
const actionSpaces = {
  buyArtifact: {
    x: 117,
    y: 270
  },
  ingredients: {
    x: 570,
    y: 468
  },
  transmute: {
    x: 385,
    y: 468
  },
  debunkTheory: {
    x: 117,
    y: 71
  },
  publishTheory: {
    x: 304,
    y: 71
  },
  testStudent: {
    x: 503,
    y: 71
  },
  testSelf: {
    x: 688,
    y: 71
  }
}
export default {
  name: "Alchemists",
  props: ["view", "actions", "context"],
  components: {
  },
  methods: {
  },
  watch: {
  },
  data() {
    return {
      path: "https://d3ux78k3bc7mem.cloudfront.net/games/alc/",
      artifactIds: {
        "Periscope": 0,
        "Magic Mortar": 1,
        "Boots of Speed": 2,
        "Discount Card": 3,
        "Printing Press": 4,
        "Robe of Respect": 5
      }
    }
  },
  computed: {
    boardId() {
      return this.context.players.length >= 4 ? '4' : '2'
    },
    turnPickerChosen() {
      return this.view.turnPicker.options.filter(e => e.chosenBy != null);
    },
    artifacts() {
      let result = [];
      for (let artifact of this.view.buyArtifact.forSale) {
        if (this.artifactIds[artifact.name]) {
          result.push(this.artifactIds[artifact.name]);
        } else {
          result.push('404-' + artifact.name);
        }
      }
      return result;
    },
    actionCubes() {
      let view = this.view;
      let cubes = [];
      for (let property of Object.keys(view)) {
        if (view[property] && typeof view[property] === 'object') {
          if (view[property].actionSpace && actionSpaces[property]) {
            let space = actionSpaces[property];
            for (let row = 0; row < view[property].actionSpace.rows.length; row++) {
              if (view[property].actionSpace.rows[row] === null) continue;
              let player = view[property].actionSpace.rows[row].first;
              for (let i = 0; i < view[property].actionSpace.rows[row].second.length; i++) {
                let visible = view[property].actionSpace.rows[row].second[i] !== null;
                cubes.push({ space: property, x: space.x + i * 30, y: space.y + row*30, visible, player });
              }
            }
          }
        }
      }
      return cubes;
    }
  }
};
//@import "../../assets/active-player.css";
//@import "../../assets/games-style.css";
//@import "../../assets/games-animations.css";
</script>
<style scoped>
.board {
  position: relative;
  background-image: url("https://d3ux78k3bc7mem.cloudfront.net/games/alc/board2.jpg");
  border: 0;
  width: 1157px;
  height: 605px;
}
.board img {
  position: absolute;
}
.hidden {
  display: none;
}
.grayed {
  opacity: 0.5;
}

.ingredient-deck {
  font-size: 30pt;
  line-height: 92px;
  position: absolute;
  top: 329px;
  left: 995px;
  background-image: url("https://d3ux78k3bc7mem.cloudfront.net/games/alc/ingd_back_rotate.png");
  background-size: 142px 92px;
  width: 142px;
  height: 92px;
  border-radius: 10px;
  color: white;
}
.favor-deck {
  font-size: 30pt;
  line-height: 142px;
  position: absolute;
  top: 238px;
  left: 527px;
  background-image: url("https://d3ux78k3bc7mem.cloudfront.net/games/alc/favour_back.jpg");
  background-size: 92px 142px;
  width: 92px;
  height: 142px;
  border-radius: 10px;
  color: white;
}
</style>
