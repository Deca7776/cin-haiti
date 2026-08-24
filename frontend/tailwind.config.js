/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Palette alignée sur la charte EPS (app hôte) plutôt que sur l'identité ONI/drapeau
        // haïtien : ce widget s'affiche à l'intérieur du shell EPS, il doit s'y fondre plutôt
        // que trancher par ses propres couleurs de marque.
        oni: {
          blue: '#1B5FA8',
          blueDark: '#134A85'
        }
      },
      fontFamily: {
        sans: ['Inter', 'Segoe UI', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 1px 2px rgba(15, 23, 42, 0.04), 0 8px 24px -8px rgba(15, 23, 42, 0.08)'
      }
    }
  },
  plugins: []
}
