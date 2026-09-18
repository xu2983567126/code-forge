export const validateTag = (value: string) => {
  if (!value || value.trim().length === 0) {
    return '标签不能为空'
  }
  if (value.length > 20) {
    return '标签长度不能超过20个字符'
  }
  if (value.includes(',') || value.includes('，')) {
    return '标签不能包含逗号分隔符'
  }
  return true
}