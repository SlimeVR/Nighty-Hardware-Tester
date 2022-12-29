/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "#0D0D1D",
        card: "#14191E",
        field: "#202B32",
      },
      width: {
        '4/12-without-gap': 'calc(33.333333% - 1rem)'
      }
    },
  },
  plugins: [],
};
