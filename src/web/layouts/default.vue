<template>
    <div class="page">
        <div class="page__header">
            <header class="page__header-content header">
                <nuxt-link class="header__title page-title" to="/">
                    <h1 class="page-title__main">WDumper</h1>
                    <Logo class="page-title__logo"></Logo>
                </nuxt-link>

                <nav class="header__nav" aria-label="Primary">
                    <ul class="nav">
                        <li class="nav__item"><nuxt-link active-class="nav__link--current" class="nav__link" to="/about">About</nuxt-link></li>
                        <li class="nav__item"><nuxt-link active-class="nav__link--current" class="nav__link" to="/dumps">Recent</nuxt-link></li>
                        <li class="nav__item"><nuxt-link active-class="nav__link--current" class="nav__link nav__link--highlight" to="/create">Create</nuxt-link></li>
                    </ul>
                </nav>

                <FormSearch class="header__search" />
            </header>
        </div>

         <main class="page__main">
            <nuxt />
        </main>
    </div>
</template>

<script>
    import Logo from "@/components/Logo.vue";
    import FormSearch from "@/components/FormSearch";

    export default {
        components: {
            Logo,
            FormSearch,
        },

        computed: {
            pageMeta() {
                return this.$route.matched[0].components.default.options.meta || {};
            }
        }

    }
</script>

<style lang="scss">
    @import "~assets/_base.scss";

    // font sizes
    h1 { font-size: 1.8rem;   }
    h2 { font-size: 1.5rem;   }
    h3 { font-size: 1.2rem;   }
    h4 { font-size: 1.125rem; }
    h5 { font-size: 1rem;     }
    h6 { font-size: 1rem;     }

    h2,h3,h4,h5,h6,body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Lato, Helvetica, Arial, sans-serif;
    }

    h1 {
        font-family: serif;
    }

    // the base layout of the page: header and main content, with max-width for wide screens
    .page {
        @at-root &__header {
            background-color: $color-base80;
            box-shadow: inset $box-shadow-base;
            overflow: auto;
        }

        @at-root &__header-content {
            max-width: $page-max-width;
            margin: 0.25rem auto;
            padding: 0 1rem;
        }

        @at-root &__main {
            background-color: $color-base100;
            max-width: $page-max-width;
            margin: 1.5rem auto 0 auto;
            padding: 0 1rem;
        }
    }

    // page header: title, nav and search form
    .header {
        display: flex;
        min-height: 3rem;
        align-items: center;
        flex-wrap: wrap;

        @at-root &__title {
            align-self: center;
            margin-right: 2rem;
        }

        @at-root &__nav {
            margin-right: auto;
            height: 3rem;
            display: block;
            margin-bottom: -0.5rem;
        }
    }

    // page title: logo + name of application
    .page-title {
        white-space: nowrap;
        text-decoration: none;
        color: black;

        @at-root &__main, &__logo {
            display: inline;
        }

        @at-root &__main {
            vertical-align: baseline;
        }

        @at-root &__logo {
            vertical-align: top;
            margin-left: 1rem;
            height: 2rem;
        }
    }

    // navigation
    .nav {
        display: flex;
        height: 100%;

        @at-root &__item {
            height: 100%;
        }

        @at-root &__link {
            display: flex;
            align-items: center;
            color: black;
            padding: 0.25rem 0.5rem;
            height: 100%;
            border-radius: 5px 5px 0 0;

            &:hover {
                color: $color-accent50;
            }

            &:active {
                outline: none;
            }
        }

        // some links in the navigation can be highlighted to make them stand out as most important
        @at-root &__link--highlight {
            color: $color-green30;
            &:hover {
                color: $color-green50;
            }
        }

        // style for the link to the currently visited page
        @at-root &__link--current {
            background-color: $color-base100;
            box-shadow: 0.1rem 0.25rem 0.2rem $color-shadow;
        }
    }
</style>
