/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#151515",
        paper: "#f4efe7",
        salt: "#f15f4a",
        kelp: "#2f7d6d",
        brass: "#c49237",
        graphite: "#2b2d31"
      },
      boxShadow: {
        soft: "0 18px 50px rgba(0,0,0,0.18)"
      }
    },
  },
  plugins: [],
};
