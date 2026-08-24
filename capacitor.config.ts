import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'app.sonicsound',
  appName: 'SonicSound',
  webDir: 'build',
  server: {
    androidScheme: 'https',
  },
};

export default config;
