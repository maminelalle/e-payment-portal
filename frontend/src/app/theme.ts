import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/** Thème Aura recoloré selon le design system (bleu sceau #1D44B8, contrôles contour 3:1). */
export const EPaymentPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef3ff',
      100: '#dce6ff',
      200: '#bccfff',
      300: '#8fb0ff',
      400: '#5f86f0',
      500: '#3a62d9',
      600: '#1d44b8',
      700: '#163791',
      800: '#122c73',
      900: '#0f245c',
      950: '#0a1638',
    },
    focusRing: { width: '2px', style: 'solid', color: '{primary.color}', offset: '2px' },
    colorScheme: {
      light: {
        primary: {
          color: '{primary.600}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.700}',
          activeColor: '{primary.800}',
        },
        highlight: {
          background: '{primary.50}',
          focusBackground: '{primary.100}',
          color: '{primary.600}',
          focusColor: '{primary.700}',
        },
        formField: { borderColor: '#8c95a6', hoverBorderColor: '#5d6779' },
      },
      dark: {
        primary: {
          color: '{primary.300}',
          contrastColor: '#0b1120',
          hoverColor: '{primary.200}',
          activeColor: '{primary.100}',
        },
        highlight: {
          background: '#18264a',
          focusBackground: '#1f3160',
          color: '{primary.300}',
          focusColor: '{primary.200}',
        },
        formField: { borderColor: '#5a6782', hoverBorderColor: '#8f9ab0' },
      },
    },
  },
});
