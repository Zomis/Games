<template>
  <svg :width="width" :height="height">
    <g v-for="(hex, index) in hexes" :key="index" @click="click(hex)">
      <polygon
        :points="hex.points"
        :fill="hex.fill"
        :stroke="hex.stroke || '#fff'"
        :stroke-width="hex.stroke !== false ? 3 : 1"
      />
      <text
        v-if="hex.cube !== null"
        :x="hex.x"
        :y="hex.y + 4"
        text-anchor="middle"
        font-size="10"
        fill="#f00"
      >
        {{ hex.cube }}
      </text>
      <text
        v-if="hex.discs.length > 0"
        :x="hex.x"
        :y="hex.y - 4"
        text-anchor="middle"
        font-size="10"
        fill="#00ff00"
      >
        {{ hex.discs }}
      </text>
      <text
        v-if="hex.structure"
        :x="hex.x"
        :y="hex.y + 14"
        text-anchor="middle"
        font-size="10"
        :fill="colors[hex.structure.color]"
      >
        {{ hex.structure.type }}
      </text>
      <text
        v-if="hex.animal"
        :x="hex.x"
        :y="hex.y - 10"
        text-anchor="middle"
        font-size="8"
        fill="black"
      >
        {{ hex.animal }}
      </text>
    </g>
  </svg>
</template>
<script>
const size = 25;
const terrains = {
  Water: '#009fff',
  Desert: "#909000",
  Swamp: "#7f007f",
  Mountain: "#7f7f7f",
  Forest: "#009000",
}
const strokes = {
  Bear: 'black',
  Cougar: 'red',
}
function cube_to_axial(cube) {
  var q = cube.q
  var r = cube.r
  return { q, r }
}

function axial_to_oddq(hex) {
  var col = hex.q
  var row = hex.r + (hex.q - (hex.q&1)) / 2
  return { x: col, y: row }
}

function oddq_to_axial(point) {
  var q = point.x
  var r = point.y - (point.x - (point.x&1)) / 2
  return { q, r }
}

function flat_hex_to_pixel(hex) {
  var x = size * (     3./2 * hex.q                    )
  var y = size * (Math.sqrt(3)/2 * hex.q  +  Math.sqrt(3) * hex.r)
  return { x, y }
}

function axial_to_cube(hex) {
  var q = hex.q
  var r = hex.r
  var s = -q-r
  return { q, r, s }
}

console.log(axial_to_cube, flat_hex_to_pixel, oddq_to_axial, axial_to_cube, axial_to_oddq, cube_to_axial);

export default {
  name: "CryptidHexGrid",
  props: ["hexGrid", "onClick"],
  data() {
    return {
      hexSize: size,
      width: 500,
      height: 500,
      offset: { x: 50, y: 50 },
      colors: {
        Blue: '#0000ff',
        Green: '#00ff00',
        White: '#ffffff',
        Black: '#000000',
      }
    };
  },
  computed: {
    hexes() {
      if (!this.hexGrid) return [];
      return this.hexGrid.map(h => {
        let hex = h.hex;
        let pos = this.axialToPixel(hex.q, hex.r);
        let cx = pos.x + this.offset.x;
        let cy = pos.y + this.offset.y;
        return {
          x: cx,
          y: cy,
          q: hex.q,
          r: hex.r,
          points: this.getHexPoints(cx, cy),
          cube: h.value.cube,
          terrain: h.value.terrain,
          fill: terrains[h.value.terrain],
          structure: h.value.structure,
          stroke: h.value.animal ? strokes[h.value.animal] : false,
          discs: h.value.discs,
          animal: h.value.animal,
        }
      });
    }
  },
  methods: {
    click(hex) {
      this.onClick({ q: hex.q, r: hex.r });
    },
    axialToPixel(q, r) {
      const size = this.hexSize;
      const x = size * 3/2 * q;
      const y = size * Math.sqrt(3) * (r + q / 2);
      return { x, y };
    },
    getHexPoints(cx, cy) {
      const points = [];
      const size = this.hexSize;
      for (let i = 0; i < 6; i++) {
        const angle = Math.PI / 180 * (60 * i);
        const x = cx + size * Math.cos(angle);
        const y = cy + size * Math.sin(angle);
        points.push(`${x},${y}`);
      }
      return points.join(" ");
    },
  },
};
</script>

<style scoped>
svg {
  background: #fafafa;
}
</style>