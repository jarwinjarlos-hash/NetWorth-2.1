/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
      colors: {
        background: '#0F172A', // Slate-900 (matches app theme)
        surface: '#1E293B',    // Slate-800
        primary: '#3B82F6',    // Blue-500
        success: '#10B981',    // Emerald-500
        successLight: '#4ADE80', // Lighter Green (requested for deposits)
        danger: '#EF4444',     // Red-500
        warning: '#F97316',    // Orange-500
      }
    },
  },
  plugins: [],
}
