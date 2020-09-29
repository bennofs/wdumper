
export default {
  mode: 'universal',
  srcDir: 'src/web',
  /*
  ** Headers of the page
  */
  head: {
    title: process.env.npm_package_name || '',
    meta: [
      { charset: 'utf-8' },
      { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      { hid: 'description', name: 'description', content: process.env.npm_package_description || '' }
    ],
    link: [
      { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' }
    ]
  },
  /*
  ** Customize the progress-bar color
  */
  loading: { color: '#fff' },
  /*
  ** Global CSS
  */
  css: [
      "minireset.css",
  ],
  /*
  ** Plugins to load before mounting the App
  */
  plugins: [
      '~/plugins/composition.js'
  ],
  /*
  ** Nuxt.js dev-modules
  */
  buildModules: [
    '@nuxt/typescript-build',
    '@nuxtjs/proxy',
  ],
  /*
  ** Proxy for development
  */
  proxy: {
    "/api": "http://localhost:5050",
  },
  /*
  ** Build configuration
  */
  build: {
    /*
    ** You can extend webpack config here
    */
    extend (config, ctx) {
	    /*const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
	    config.plugins.push(new BundleAnalyzerPlugin());*/
    },

    postcss: {
      plugins: {
        'postcss-import': false
      }
    }
  },

  /*
   ** Output directory of the build
   */
  buildDir: "build/nuxt",
}
