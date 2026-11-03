package com.xiaojie.englishlearning.category;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<String> list() {
        return categoryService.list();
    }

    @GetMapping("/browse")
    public List<CategoryService.CategorySummary> browse(
            @RequestParam(value = "level", required = false) String level) {
        return categoryService.browse(level);
    }
}
