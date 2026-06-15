<template>
  <div>
    <div class="sidebar-logo-container">
      <!-- <img
        src="./../../../assets/logo.png"
        width="122.5"
        alt=""
      > -->
      <!-- <img
        src="@/assets/login/login-logo.png"
        alt=""
        style="width: 120px; height: 31px"
      /> -->
      <div v-if="!isCollapse"
           class="sidebar-logo">
        <img src="@/assets/images/logo.png">
      </div>
      <div v-else
           class="sidebar-logo-mini">
        <img src="@/assets/images/logo.png">
      </div>
    </div>
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu :default-openeds="defOpen"
               :default-active="defAct"
               :collapse="isCollapse"
               :background-color="variables.menuBg"
               :text-color="variables.menuText"
               :active-text-color="variables.menuActiveText"
               :unique-opened="false"
               :collapse-transition="false"
               mode="vertical">
        <sidebar-item v-for="route in routes"
                      :key="route.path"
                      :item="route"
                      :base-path="route.path"
                      :is-collapse="isCollapse" />
        <!-- <div class="sub-menu">
          <div class="avatarName">
            {{ name }}
          </div>
          <div class="img">
            <img
              src="./../../../assets/icons/btn_close@2x.png"
              class="outLogin"
              alt="退出"
              @click="logout"
            />
          </div>
        </div> -->
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import { AppModule } from '@/store/modules/app'
import { UserModule } from '@/store/modules/user'
import SidebarItem from './SidebarItem.vue'
import variables from '@/styles/_variables.scss'
import { getSidebarStatus, setSidebarStatus } from '@/utils/cookies'
import Cookies from 'js-cookie'
@Component({
  name: 'SideBar',
  components: {
    SidebarItem
  }
})
export default class extends Vue {
  private restKey: number = 0
  get name() {
    return (UserModule.userInfo as any).name
      ? (UserModule.userInfo as any).name
      : JSON.parse(Cookies.get('user_info') as any).name
  }
  get defOpen() {
    // const urlArr = this.$route.path.split('/')
    // const openStr = urlArr.length > 2 ? `/${urlArr[1]}` : '/'
    let path = ['/']
    this.routes.forEach((n: any, i: number) => {
      if (n.meta.roles && n.meta.roles[0] === this.roles[0]) {
        path.splice(0, 1, n.path)
      }
    })
    return path
  }

  get defAct() {
    let path = this.$route.path
    return path
  }

  get sidebar() {
    return AppModule.sidebar
  }

  get roles() {
    return UserModule.roles
  }

  get routes() {
    let routes = JSON.parse(
      JSON.stringify([...(this.$router as any).options.routes])
    )
    console.log('-=-=routes=-=-=', routes)
    console.log('-=-=routes=-=-=', this.roles[0])
    let menuList = []
    let menu = routes.find(item => item.path === '/')
    if (menu) {
      menuList = menu.children
    }
    console.log('-=-=routes=-wwww=-=', routes)
    return menuList
  }

  get variables() {
    return variables
  }

  get isCollapse() {
    return !this.sidebar.opened
  }
  private async logout() {
    this.$store.dispatch('LogOut').then(() => {
      // location.href = '/'
      this.$router.replace({ path: '/login' })
    })
    // this.$router.push(`/login?redirect=${this.$route.fullPath}`)
  }
}
</script>

<style lang="scss" scoped>
/* 外层容器 */
.sidebar-logo-container {
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #ffffff !important;
  overflow: hidden;
}

/* 展开状态 Logo */
.sidebar-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0 12px;
  background: transparent !important;

  ::v-deep img {
    height: 60px !important;
    width: auto !important;
    max-height: none !important;
    max-width: none !important;
    object-fit: contain;
    background: transparent !important;
    mix-blend-mode: multiply;
    image-rendering: -webkit-optimize-contrast;
  }
}

/* 折叠状态 Logo */
.sidebar-logo-mini {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  background: transparent !important;

  ::v-deep img {
    height: 32px !important;
    width: auto !important;
    max-height: none !important;
    max-width: none !important;
    object-fit: contain;
    background: transparent !important;
    mix-blend-mode: multiply;
    image-rendering: -webkit-optimize-contrast;
  }
}

.el-scrollbar {
  height: 100%;
  background-color: rgb(52, 55, 68);
}

.el-menu {
  border: none;
  height: calc(95vh - 23px);
  width: 100% !important;
  padding: 47px 15px 0;
}
</style>
