<template>
    <div class="page">
        <div class="topnav">
            <header>
                <span class="topnav-home">
                    <nuxt-link class="home-link" to="/">WDumper</nuxt-link>
                    <Logo class="home-logo"/>
                    <span v-if="this.$route.name !== 'index'" class="home-title">
                        {{ pageMeta.title || "" }}
                    </span>
                </span>

                <span class="topnav-menu">
                    <Search class="menu-search"></Search>

                    <nuxt-link to="/about">about</nuxt-link>
                    <nuxt-link to="/dumps">recent</nuxt-link>
                    <nuxt-link class="hl-link" to="/create">new</nuxt-link>
                </span>
            </header>
        </div>

         <main id="main">
            <nuxt />
        </main>
    </div>
</template>

<script>
    import Logo from "@/components/Logo.vue";
    import Search from "@/components/Search.vue";

    export default {
        components: {
            Logo,
            Search
        },

        computed: {
            pageMeta() {
                return this.$route.matched[0].components.default.options.meta || {};
            }
        }

    }
</script>

<style lang="scss">
    @import "~assets/base.scss";

    body {
        background-color: $color-base100;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Lato, Helvetica, Arial, sans-serif;
    }

    .topnav {
        background-color: $color-base90;
        box-shadow: $box-shadow-base;
        height: 3.5rem;
        margin-bottom: 1.5rem;

        a {
            text-decoration: none;
            color: black;
        }

        header {
            display: flex;
            align-items: center;
            height: 100%;
        }


        .topnav-home {
            .home-logo {
                height: 2rem;
                vertical-align: baseline;
                margin-left: 1rem;
            }

            .home-link {
                font-family: serif;
                vertical-align: baseline;
            }

            .home-title {
                font-size: 1.5rem;
                margin-left: 1rem;
                vertical-align: baseline;
            }

            white-space: nowrap;
        }

        .topnav-menu {
            margin-left: auto;
            .menu-search {
                margin-right: 1rem;
                display: inline-block;
                vertical-align: baseline;
            }

            a {
                padding: 0.25rem;
                border-radius: 0.2rem;
                text-decoration: underline;

                &:hover {
                    color: $color-accent50;
                }

                &:active {
                    outline: none;
                }
            }

            .hl-link {
                color: $color-green30;
                &:hover {
                    color: $color-green50;
                }
            }
        }
    }

    // base horizontal layout
    #main, .topnav header {
        max-width: $page-max-width;
        min-width: $page-min-width;
        padding-left: 1rem;
        padding-right: 1rem;
        margin: auto;
    }

    h1, .topnav-home {
        font-size: 2rem;
    }

    h2 {
        font-size: 1.5rem;
        line-height: 3rem;
    }

    p {
        line-height: 1.5em;
        display: block;

        margin-bottom: 0.5em;

    }
</style>
