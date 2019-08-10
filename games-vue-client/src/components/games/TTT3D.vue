<template>
  <Scene @complete="initialized" v-model="scene" @pointer$="onPointer">
<!--    <HemisphericLight emissive="#00FF00"></HemisphericLight>-->
    <HemisphericLight diffuse="#fff" />
    <Camera type="arcRotate"></Camera>
    <template v-for="x in indices">
      <template v-for="y in indices">
        <Cylinder :position="[positions[x], positions[y], 0]" :scaling="[0.3, 0.3, 5]">
          <Material diffuse="#fff" :metallic="0" :roughness="1"> </Material>
        </Cylinder>
        <Box v-for="z in indices" :position="[positions[x], positions[y], positions[z]]"
         :key="`${x},${y},${z}`"
         @onPointerOver="pointerOver"
         :name="`box_${x}_${y}_${z}`">
          <Material diffuse="#000000" :roughness="1" :metallic="0.5">
          </Material>
        </Box>
      </template>
    </template>
  </Scene>
</template>
<script>
let SPACE = 1;
export default {
  name: "TTT3D",
  data() {
    return {
      scene: null,
      indices: [0, 1, 2, 3],
      positions: [-3, -1, 1, 3]
//      positions: [-2 - SPACE, -1 - SPACE / 2, 0 + SPACE / 2, 1 + SPACE]
//      -3     -1.5    0.5    2
    }
  },
  methods: {
    onPointer(event) {
      //console.log(event, this);
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
    onPointerDown(evt) {
      let scene = this.scene;
      let pickInfo = scene.pick(scene.pointerX, scene.pointerY, e => true);
      if (pickInfo.hit) {

        let hit = pickInfo.pickedMesh;
        let splitted = hit.name.split("_");
        if (splitted.length !== 4) {
          return;
        }
        let y = parseInt(splitted[1], 10);
        let x = parseInt(splitted[2], 10);
        let z = parseInt(splitted[3], 10);
        console.log(hit, hit.name, x, y, z);
        let b = hit.material.albedoColor.b;
        let r = hit.material.albedoColor.r;
        console.log("Current:", r, b);

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
      }
    },
    pointerOver(evt) {
      console.log("POINT_OVER", evt);
    }
  }
}
</script>
