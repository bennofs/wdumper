// stub webpack config file for IDE
module.exports = {
    resolve: {
        // for IDE (WebStorm, PyCharm, etc)
        alias: {
            '@': path.resolve(__dirname, 'src/web'),
            'assets': path.resolve(__dirname, 'src/web/assets'),
        }
    }
};
