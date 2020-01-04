<template>
  <div class="game-ttt3d">
    <GameHead :gameInfo="gameInfo"></GameHead>
    <GameResult :gameInfo="gameInfo"></GameResult>
  <Scene @complete="initialized" v-model="scene" @pointer$="onPointer" v-if="view.board">
<!--    <HemisphericLight emissive="#00FF00"></HemisphericLight>-->
    <HemisphericLight diffuse="#fff" />
    <Camera type="arcRotate"></Camera>
    <template v-for="y in indices">
      <template v-for="x in indices">
        <Cylinder :key="`${y},${x},cylinder`" :position="[positions[y], 0, positions[x]]" :scaling="cylinderScaling">
          <Material diffuse="#505050" :metallic="0" :roughness="1"> </Material>
        </Cylinder>
        <Sphere v-for="(color, z) in colors[y][x]" :position="[positions[y], positions[z], positions[x]]"
         :key="`${y},${x},${z}`"
         @onPointerOver="pointerOver"
         :name="`box_${y}_${x}_${z}`">
          <Material :diffuse="color" :roughness="1" :metallic="0.5">
          </Material>
        </Sphere>
      </template>
    </template>
  </Scene>
  </div>
</template>
<script>
//let SPACE = 1;
import GameHead from "./common/GameHead";
import GameResult from "./common/GameResult";
import { mapState } from "vuex";

export default {
  name: "TTT3D",
  props: ["gameInfo", "showRules"],
  data() {
    return {
      scene: null,
      cylinderScaling: [0.3, 2.0, 0.3],
      indices: [0, 1, 2, 3],
      positions: [-2.1, -0.7, 0.7, 2.1]
//      positions: [-2 - SPACE, -1 - SPACE / 2, 0 + SPACE / 2, 1 + SPACE]
//      -3     -1.5    0.5    2
    }
  },
  components: {
    GameHead,
    GameResult
  },
  mounted() {
    this.$store.dispatch("viewRequest", this.gameInfo)
  },
  methods: {
    onPointer(event) {
      console.log(event, this);
/*      let scene = this.scene;
      let pick = scene.pick(scene.pointerX, scene.pointerY, e => true);
      if (pick.hit) {
        console.log("onPointer", pick);
      }*/
    },
    initialized(evt) {
      console.log("test", evt, this.scene);
      let sc = evt.scene.getEngine().getRenderingCanvas();
      console.log("sc", sc);
      this.scene = evt.scene;
      sc.addEventListener("pointerdown", this.onPointerDown, false);
    },
    onPointerDown() {
      let scene = this.scene;
      let pickInfo = scene.pick(scene.pointerX, scene.pointerY, () => true);
      if (pickInfo.hit) {

        let hit = pickInfo.pickedMesh;
        let splitted = hit.name.split("_");
        if (splitted.length !== 4) {
          return;
        }
        let y = parseInt(splitted[1], 10);
        let x = parseInt(splitted[2], 10);
        let z = parseInt(splitted[3], 10);
        console.log(hit, hit.name, y, x, z);
        let b = hit.material.albedoColor.b;
        let r = hit.material.albedoColor.r;
        console.log("Current:", r, b);

        this.$store.dispatch("makeMove", { gameInfo: this.gameInfo, moveType: "play", move: { x: x, y: y } });
/*
        if (r == 0 && b == 0) {
          hit.material.albedoColor.r = 0;
          hit.material.albedoColor.b = 1;
        } else if (b == 1 && r == 0) {
          hit.material.albedoColor.r = 1;
          hit.material.albedoColor.b = 0;
        } else {
          hit.material.albedoColor.r = 0;
          hit.material.albedoColor.b = 0;
        }
*/
      }
    },
    pointerOver(evt) {
      console.log("POINT_OVER", evt);
    }
  },
  computed: {
    ...mapState("DslGameState", {
      view(state) {
        return state.games[this.gameInfo.gameId].gameData.view;
      }
    }),
    colors() {
      // {"type":"GameView","gameType":"DSL-TTT3D","gameId":"1","viewer":1,"view":{"currentPlayer":1,"winner":0,"board":[[{"row":[0,0,0,null]},{"row":[1,null,null,null]},{"row":[0,1,null,null]},{"row":[1,0,null,null]}],[{"row":[null,null,null,null]},{"row":[1,1,0,1]},{"row":[null,null,null,null]},{"row":[0,0,1,null]}],[{"row":[0,0,0,null]},{"row":[null,null,null,null]},{"row":[null,null,null,null]},{"row":[1,0,1,1]}],[{"row":[null,null,null,null]},{"row":[null,null,null,null]},{"row":[1,1,null,null]},{"row":[1,0,0,null]}]]}}
      return this.view.board.map(yy => {
        return yy.map(xx => {
          let row = [];
          for (let z = 0; z < xx.row.length; z++) {
            let zz = xx.row[z];
            row.push(zz == null ? "#000000" : zz == 0 ? "#ff0000" : "#0000ff");
            let previousNotEmpty = (z === 0) || (xx.row[z - 1] !== null)
            if (zz === null && previousNotEmpty) {
              return row;
            }
          }
          return row;
        })
      })
    }
  }
}
</script>
