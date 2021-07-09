<template>
  <v-card
    elevation="4"
    :class="['set-card', 'fill-' + card.color, 'fadeIn', selectedClass]"
    @click="click"
  >
    <transition
      name="slide-fade"
      mode="out-in"
    >
      <v-card-text class="slide-fade-item card-content">
        <svg
          v-for="shape in shapes"
          :key="shape.key"
          viewbox="-2 -2 54 104"
        >
          <path
            :d="shape.d"
            :fill="shape.fill"
          />
        </svg>
      </v-card-text>
    </transition>
  </v-card>
</template>

<script>
const paths = {
  diamond: "M25 0 L50 50 L25 100 L0 50 Z",
  squiggly: "M38.4,63.4c0,16.1,11,19.9,10.6,28.3c-0.5,9.2-21.1,12.2-33.4,3.8s-15.8-21.2-9.3-38c3.7-7.5,4.9-14,4.8-20 c0-16.1-11-19.9-10.6-28.3C1,0.1,21.6-3,33.9,5.5s15.8,21.2,9.3,38C40.4,50.6,38.5,57.4,38.4,63.4z",
  ellipse: "M25,99.5C14.2,99.5,5.5,90.8,5.5,80V20C5.5,9.2,14.2,0.5,25,0.5S44.5,9.2,44.5,20v60 C44.5,90.8,35.8,99.5,25,99.5z"
}

const colors = {
  red: "#ef476f",
  purple: "#7057ff",
  green: "#06D6A0",
}

const fill = (card) => {
  if (card.filling == "striped") {
    return 'url(#striped-' + card.color + ')'
  } else if (card.filling == "clear") {
    return 'none'
  } else if (card.filling == "filled") {
    return colors[card.color]
  }

  return 'none'
}

export default {
  name: "SetCard",
  props: ["card", "onClick", "selected"],
  methods: {
    click() {
      this.onClick(this.card);
    }
  },
  computed: {
    selectedClass() {
      return this.selected ? "selected" : "not-selected";
    },
    color() {
      return colors[this.card.color]
    },
    shapes() {
      const card = this.card
      return Array.from({ length: card.count }).map((val, key) => {
        return {
          key,
          fill: fill(card),
          d: paths[card.shape],
        }
      })
    },
  },
}
</script>
<style scoped>
.set-card {
    margin: 5px;
    width: calc(33.33% - 10px);
    padding-top: 20%;
    cursor: pointer;
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 5px 10px -5px rgba(black, .2);
    position: relative;
    transition: .2s ease-in-out;
    opacity: 0;
    perspective: 1000px;
    transform-origin: left center;
    transform: rotateY(60deg);
    transform-style: preserve-3d;
}

.set-card.selected {
    background: #dddddd;
}

.set-card .card-content {
    position: absolute;
    height: 100%;
    width: 100%;
    top: 0;
    padding: 10px;
    display: flex;
    justify-content: center;
}

.set-card.fadeIn {
    opacity: 1;
    transform: rotateY(0deg);
}

.set-card svg {
    height: 100%;
    margin: 0 2%;
}

.set-card path,
.set-card rect {
    stroke-width: 2.5;
}

.fill-green path, .fill-green rect { stroke: #06D6A0; }
.fill-purple path, .fill-purple rect { stroke: #7057ff; }
.fill-red path, .fill-red rect { stroke: #ef476f; }
</style>
