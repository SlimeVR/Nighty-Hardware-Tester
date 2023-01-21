/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "#0A0A10",
        card: "#161720",
        field: "#202B32",
      },
      fontFamily: {
        sans: ['"Roboto"', "sans-serif"],
        mono: ['"Roboto Mono"', "monospace"],
      },
    },
  },
  plugins: [],
};
