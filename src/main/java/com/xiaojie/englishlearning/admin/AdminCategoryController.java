package com.xiaojie.englishlearning.admin;

import com.xiaojie.englishlearning.category.CategoryService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> add(@RequestBody(required = false) CategoryRequest body)
            throws IOException {
        String name = body == null ? null : body.getName();
        categoryService.add(name);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.list());
    }

    @DeleteMapping
    public ResponseEntity<ApiError> remove(@RequestParam("name") String name) throws IOException {
        if (categoryService.isUsed(name)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("这个分类正在被素材使用，删除前请先修改对应素材。"));
        }
        if (!categoryService.remove(name)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("分类不存在。"));
        }
        return ResponseEntity.noContent().build();
    }

    public static class CategoryRequest {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
