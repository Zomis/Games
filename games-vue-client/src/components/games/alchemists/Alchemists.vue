<template>
  <v-container fluid>
    <v-row>
      <v-col align-self="start">
        <v-card>
          <div class="board" :class="'players-' + context.players.length">
            <!--
            <img v-for="(cube, cubeIndex) in actionCubes" :key="'cube-' + cubeIndex"
             :src="`${path}cube_${cube.player}.png`"
             :style="{ left: cube.x + 'px', top: cube.y + 'px' }" :class="{ grayed: !cube.visible }" />
             -->

            <div class="turn-order-placement">
              <template v-for="(turnOrder, turnOrderIndex) in view.turnPicker.options">
                <img v-if="turnOrder.chosenBy != null"
                  class="chosen"
                :class="turnOrder.key"
                  :key="'turn-' + turnOrderIndex"
                  :src="`${path}cube_${turnOrder.chosenBy}.png`"
                  :style="{ top: (30*turnOrderIndex) + 'px' }"
                />
                <div v-else-if="view.turnPicker.actionable[turnOrder.key]" :key="'turn-' + turnOrderIndex"
                  class="choosable"
                :class="turnOrder.key"
                  :style="{ top: (30*turnOrderIndex) + 'px' }"
                  @click="chooseTurnOrder(turnOrder.key)"
                />
                <img v-else class="hidden" :key="'turn-' + turnOrderIndex"
                :class="turnOrder.key"
                  :style="{ top: (30*turnOrderIndex) + 'px' }"
                />
              </template>
            </div>
            <div class="artifacts-favor-flexbox d-flex">
              <Artifact v-for="(artifact, artifactIndex) in artifacts" :key="'artifact-' + artifactIndex" :name="artifact" />
              <div class="favor-deck label ml-auto gamecard">{{ view.favors.deck }}</div>
            </div>

            <div class="ingredients-flexbox d-flex flex-column justify-space-between">
              <div class="md-auto d-flex justify-end">
                <!-- position top-right -->
                <div class="ingredients-deck label">{{ view.ingredients.deck }}</div>
              </div>
              <div class="d-flex justify-space-between">
                <!-- position bottom, from left to right -->
                <Ingredient v-for="(ingredient, ingredientIndex) in view.ingredients.slots" :key="'ingredient-' + ingredientIndex" :id="ingredient" />
              </div>
            </div>
            <div class="d-flex flex-column justify-end action-cube-area" v-for="actionCubeArea in actionCubeAreas" :key="actionCubeArea.name" :class="actionCubeArea.name">
              <AlchemistsActionCubesRow v-for="(row, index) in view[actionCubeArea.name].actionSpace.rows" :cubes="row" :key="index" />
      <!--
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
      return cubes; -->

            </div>

            <div class="sell-area">
              <img v-if="view.sellPotion.heroes[0]" :style="{ left: '8px', top: '392px', width: '93px', height: '144px' }"
               :src="`${path}hero_n${view.sellPotion.heroes[0].id}.jpg`" />

              <img v-if="view.sellPotion.heroes[1]" :style="{ left: '-108px', top: '392px', width: '93px', height: '144px' }"
               :src="`${path}hero_n${view.sellPotion.heroes[1].id}.jpg`" />
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>
    <v-row justify="center">
      <div>
        <h3>General info</h3>
        {{ view.phase }}
        {{ view.stack }}
        {{ view.master }}
        {{ view.round }}
        {{ view.solution }}
        {{ view.favors }}
      </div>
      <div>
        <h3>Players</h3>
        <Favor name="ASSOCIATE" />
        {{ view.startingPlayer }}
        {{ view.actionCubeCount }}
        {{ view.players }}
      </div>
      <div>
        <h3>Turn picker</h3>
        {{ view.turnPicker }}
      </div>
      <div>
        <h3>Theory board</h3>
        {{ view.theoryBoard }}
      </div>
      <div>
        <h3>Spaces</h3>
        <p>{{ view.ingredients }}</p>
        <p>{{ view.transmute }}</p>
        <p>{{ view.custodian }}</p>
        <p>{{ view.sellPotion }}</p>
        <p>{{ view.buyArtifact }}</p>
        <p>{{ view.debunkTheory }}</p>
        <p>{{ view.publishTheory }}<p>
        <p>{{ view.testStudent }}</p>
        <p>{{ view.testSelf }}</p>
        <p>{{ view.exhibition }}</p>
      </div>
    </v-row>
  </v-container>
</template>

<script>
import AlchemistsActionCubesRow from "./AlchemistsActionCubesRow";
import AlchComponents from "./AlchComponents";
const Favor = AlchComponents.Favor;
const Ingredient = AlchComponents.Ingredient;
const Artifact = AlchComponents.Artifact;
//import PlayerProfile from "@/components/games/common/PlayerProfile"
//import CardZone from "@/components/games/common/CardZone"
/*    CardZone,
    PlayerProfile,
    HanabiCard*/
export default {
  name: "Alchemists",
  props: ["view", "actions", "context"],
  components: {
    AlchemistsActionCubesRow,
    Favor, Ingredient, Artifact,
  },
  methods: {
    chooseTurnOrder(key) {
      this.actions.actionParameter("turn", key)
    }
  },
  watch: {
  },
  data() {
    return {
      path: "https://d3ux78k3bc7mem.cloudfront.net/games/alc/",
    }
  },
  computed: {
    actionCubeAreas() {
      if (!this.view) return [];
      let result = [];
      for (let i in this.view) {
        if (this.view[i].actionSpace) {
          result.push({ "name": i });
        }
      }
      return result
    },
    boardId() {
      return this.context.players.length >= 4 ? '4' : '2'
    },
    turnPickerChosen() {
      return this.view.turnPicker.options.filter(e => e.chosenBy != null);
    },
    artifacts() {
      let result = [];
      if (!this.view) return result;
      if (!this.view.buyArtifact) return result;
      if (!this.view.buyArtifact.forSale) return result;
      for (let artifact of this.view.buyArtifact.forSale) {
        if (this.artifactIds[artifact.name] !== undefined) {
          result.push(this.artifactIds[artifact.name]);
        } else {
          result.push('404-' + artifact.name);
        }
      }
      return result;
    },
    actionCubes() {
      return [];
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

.hidden {
  display: none;
}
.grayed {
  opacity: 0.5;
}

.artifacts-favor-flexbox {
  top: 238px;
  left: 210px;
  width: 407px;
  height: 142px;
  position: absolute;
}

.gamecard {
  width: 92px;
  height: 142px;
}

.ingredients-flexbox {
  top: 331px;
  left: 663px;
  width: 476px;
  height: 260px;
  position: absolute;
}

.ingredients-deck {
  width: 142px;
  height: 92px;
  line-height: 92px;
  background-image: url("https://d3ux78k3bc7mem.cloudfront.net/games/alc/ingd_back_rotate.png");
  background-size: 142px 92px;
}

.label {
  font-size: 30pt;
  border-radius: 10px;
  color: white;
}

.favor-deck {
  line-height: 142px;
  background-image: url("https://d3ux78k3bc7mem.cloudfront.net/games/alc/favour_back.jpg");
  background-size: 92px 142px;
}

.action-cube-area {
  background-color: #7fffff80;
  position: absolute;
}

.action-cube-area.ingredients {
  left: 573px;
  top: 471px;
  width: 82px;
  height: 79px;
}

.action-cube-area.buyArtifact {
  left: 124px;
  top: 278px;
  width: 70px;
  height: 80px;
}

.action-cube-area.transmute {
  left: 385px;
  top: 468px;
  width: 100px;
  height: 100px;
}

.action-cube-area.debunkTheory {
  left: 117px;
  top: 71px;
  width: 100px;
  height: 100px;
}

.action-cube-area.publishTheory {
  left: 304px;
  top: 71px;
  width: 100px;
  height: 100px;
}

.action-cube-area.publishTheory {
  left: 304px;
  top: 71px;
  width: 100px;
  height: 100px;
}

.action-cube-area.testStudent {
  left: 503px;
  top: 71px;
  width: 100px;
  height: 100px;
}

.action-cube-area.testSelf {
  left: 688px;
  top: 71px;
  width: 100px;
  height: 100px;
}

.turn-order-placement {
  left: 1066px;
  top: 34px;
  width: 28px;
  height: 247px;
  background-color: #7fffff80;
  position: absolute;
}

.turn-order-placement * {
  position: absolute;
  cursor: pointer;
  width: 30px;
  height: 27px;
  left: 0px;
}

.turn-order-placement .choosable:hover {
  background-color: green;
}

.turn-order-placement .choosable {
  background-color: lime;
}

.top-areas {
  width: 621px;
  height: 81px;
  top: 72px;
  left: 121px;
}

.sell-area {
  background-color: #7f7f7f7f;
  width: 217px;
  height: 203px;
  position: absolute;
  top: 395px;
  left: 11px;
}
</style>
