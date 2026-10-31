export const ages = [
  { value: 'PUPPY', label: '幼年（1岁内）' },
  { value: 'YOUNG', label: '青年（1-3岁）' },
  { value: 'ADULT', label: '成年（3-7岁）' },
  { value: 'SENIOR', label: '老年（7岁以上）' },
]
export const allergies = [
  { value: 'NONE', label: '无已知过敏' },
  { value: 'FRAGRANCE', label: '香波香精过敏' },
  { value: 'PROTEIN', label: '蛋白质过敏' },
  { value: 'POLLEN', label: '花粉过敏' },
  { value: 'FLEA', label: '跳蚤叮咬过敏' },
]
export const skinHistories = [
  { value: 'HEALTHY', label: '皮肤健康' },
  { value: 'DANDER', label: '轻度皮屑' },
  { value: 'PODODERMATITIS', label: '趾间炎史' },
  { value: 'EAR_MITE', label: '耳螨史' },
  { value: 'FUNGAL', label: '真菌感染史' },
  { value: 'DERMATITIS', label: '过敏性皮炎史' },
]
export const groomingHistories = [
  { value: 'NONE', label: '无既往洗护' },
  { value: 'BASIC', label: '定期基础洗护' },
  { value: 'STYLING', label: '曾做造型修剪' },
  { value: 'SPA', label: '曾做深层SPA' },
  { value: 'MATTING', label: '曾处理严重打结' },
]
export const dogBreeds = ['比熊', '金毛', '柯基', '泰迪', '萨摩耶', '博美', '哈士奇', '拉布拉多', '柴犬', '边牧', '法斗', '雪纳瑞', '马尔济斯', '约克夏', '德牧', '阿拉斯加', '西高地', '喜乐蒂', '松狮', '贵宾', '中华田园犬', '其他犬种']
export const catBreeds = ['布偶猫', '英短', '美短', '橘猫', '暹罗猫', '缅因猫', '加菲猫', '金渐层', '银渐层', '蓝猫', '无毛猫', '德文卷毛猫', '俄罗斯蓝猫', '波斯猫', '孟买猫', '森林猫', '中华田园猫', '其他猫种']
export const rabbitBreeds = ['垂耳兔', '侏儒兔', '狮子兔', '安哥拉兔', '荷兰兔', '熊猫兔']
export const birdBreeds = ['玄凤鹦鹉', '虎皮鹦鹉', '牡丹鹦鹉', '金丝雀', '文鸟']
export const hamsterBreeds = ['一线仓鼠', '三线仓鼠', '布丁仓鼠', '紫仓', '老公公仓鼠']
export const coats = ['短毛', '卷毛', '长毛', '双层毛']
export const sizes = [
  { value: 'SMALL', label: '小型' },
  { value: 'MEDIUM', label: '中型' },
  { value: 'LARGE', label: '大型' },
  { value: 'GIANT', label: '巨型' },
]
export const weights = [2, 3, 4, 5, 6, 8, 10, 12, 15, 18, 22, 26, 28, 32, 36]
export const serviceCategories = ['基础洗护', '造型美容', '深层护理', '宠物专护', '猫咪专护']
export const serviceDurations = [45, 60, 75, 90, 120]
export const skinScores = [
  { value: 'CLEAR', label: '健康无红疹', score: 100 },
  { value: 'DRY', label: '轻度干燥', score: 80 },
  { value: 'FLAKY', label: '明显皮屑', score: 60 },
  { value: 'IRRITATED', label: '红疹或破损', score: 40 },
]
export const coatScores = [
  { value: 'SMOOTH', label: '柔顺无打结', score: 100 },
  { value: 'LIGHT', label: '轻微打结', score: 80 },
  { value: 'MODERATE', label: '中度打结', score: 60 },
  { value: 'MATTED', label: '严重毡化', score: 40 },
]
export const earScores = [
  { value: 'CLEAN', label: '清洁无异味', score: 100 },
  { value: 'MILD', label: '轻度分泌', score: 75 },
  { value: 'ODOR', label: '异味或红肿', score: 50 },
  { value: 'MITE', label: '疑似耳螨', score: 30 },
]

export const speciesOptions = [
  { value: 'DOG', label: '狗狗' },
  { value: 'CAT', label: '猫咪' },
  { value: '兔子', label: '兔子' },
  { value: '鸟类', label: '鸟类' },
  { value: '仓鼠', label: '仓鼠' },
]
export function speciesLabel(species?: string) {
  if (!species) return '其他'
  if (species === 'ALL') return '全种类通用'
  return speciesOptions.find(item => item.value === species)?.label || species
}
export const setupSpeciesOptions = [
  ...speciesOptions,
  { value: 'ALL', label: '全种类通用' },
]
export function breedsFor(species: string) {
  if (species === 'CAT') return catBreeds
  if (species === 'DOG') return dogBreeds
  if (species === '兔子') return rabbitBreeds
  if (species === '鸟类') return birdBreeds
  if (species === '仓鼠') return hamsterBreeds
  return []
}

export function healthScore(skin: string, coat: string, ear: string) {
  const skinScore = skinScores.find(item => item.value === skin)?.score
  const coatScore = coatScores.find(item => item.value === coat)?.score
  const earScore = earScores.find(item => item.value === ear)?.score
  if (skinScore == null || coatScore == null || earScore == null) return null
  return Math.round(skinScore * 0.4 + coatScore * 0.35 + earScore * 0.25)
}

export function termLabel(options: { value: string; label: string }[], value?: string) {
  return options.find(item => item.value === value)?.label || ''
}
